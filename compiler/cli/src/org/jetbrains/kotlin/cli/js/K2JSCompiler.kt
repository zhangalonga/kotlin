/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.cli.js

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ExceptionUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.HashMap
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.common.output.outputUtils.*
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.js.IncrementalDataProvider
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer
import org.jetbrains.kotlin.incremental.js.TranslationResultValue
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult
import org.jetbrains.kotlin.js.config.EcmaVersion
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.config.SourceMapSourceEmbedding
import org.jetbrains.kotlin.js.facade.K2JSTranslator
import org.jetbrains.kotlin.js.facade.MainCallParameters
import org.jetbrains.kotlin.js.facade.TranslationResult
import org.jetbrains.kotlin.js.facade.TranslationUnit
import org.jetbrains.kotlin.js.facade.exceptions.TranslationException
import org.jetbrains.kotlin.js.sourceMap.SourceFilePathResolver
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.utils.*

import java.io.File
import java.io.IOException
import java.util.*

import org.jetbrains.kotlin.cli.common.ExitCode.COMPILATION_ERROR
import org.jetbrains.kotlin.cli.common.ExitCode.OK
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.*

class K2JSCompiler : CLICompiler<K2JSCompilerArguments>() {

    private val performanceManager = K2JSCompilerPerformanceManager()

    override fun createArguments(): K2JSCompilerArguments {
        return K2JSCompilerArguments()
    }

    @Throws(TranslationException::class)
    protected fun translate(
        reporter: JsConfig.Reporter,
        allKotlinFiles: List<KtFile>,
        jsAnalysisResult: JsAnalysisResult,
        mainCallParameters: MainCallParameters,
        config: JsConfig
    ): TranslationResult {
        val translator = K2JSTranslator(config)
        val incrementalDataProvider = config.configuration.get(JSConfigurationKeys.INCREMENTAL_DATA_PROVIDER)
        if (incrementalDataProvider != null) {
            val nonCompiledSources = HashMap<File, KtFile>(allKotlinFiles.size)
            for (ktFile in allKotlinFiles) {
                nonCompiledSources[VfsUtilCore.virtualToIoFile(ktFile.virtualFile)] = ktFile
            }

            val compiledParts = incrementalDataProvider.compiledPackageParts

            val allSources = arrayOfNulls<File>(compiledParts.size + allKotlinFiles.size)
            var i = 0
            for (file in compiledParts.keys) {
                allSources[i++] = file
            }
            for (file in nonCompiledSources.keys) {
                allSources[i++] = file
            }
            Arrays.sort(allSources)

            val translationUnits = ArrayList<TranslationUnit>()
            i = 0
            while (i < allSources.size) {
                val nonCompiled = nonCompiledSources[allSources[i]]
                if (nonCompiled != null) {
                    translationUnits.add(TranslationUnit.SourceFile(nonCompiled))
                } else {
                    val translatedValue = compiledParts[allSources[i]]
                    translationUnits.add(TranslationUnit.BinaryAst(translatedValue!!.binaryAst))
                }
                i++
            }
            return translator.translateUnits(reporter, translationUnits, mainCallParameters, jsAnalysisResult)
        }

        allKotlinFiles.sortedBy { ktFile -> VfsUtilCore.virtualToIoFile(ktFile.virtualFile) }
        return translator.translate(reporter, allKotlinFiles, mainCallParameters, jsAnalysisResult)
    }

    override fun doExecute(
        arguments: K2JSCompilerArguments,
        configuration: CompilerConfiguration,
        rootDisposable: Disposable,
        paths: KotlinPaths?
    ): ExitCode {
        val messageCollector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)

        if (arguments.freeArgs.isEmpty() && !IncrementalCompilation.isEnabledForJs()) {
            if (arguments.version) {
                return OK
            }
            messageCollector.report(ERROR, "Specify at least one source file or directory", null)
            return COMPILATION_ERROR
        }

        val pluginLoadResult = PluginCliParser.loadPluginsSafe(arguments.pluginClasspaths, arguments.pluginOptions, configuration)
        if (pluginLoadResult != ExitCode.OK) return pluginLoadResult

        configuration.put(JSConfigurationKeys.LIBRARIES, configureLibraries(arguments, paths, messageCollector))

        configuration.addKotlinSourceRoots(arguments.freeArgs)
        val environmentForJS =
            KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JS_CONFIG_FILES)

        val project = environmentForJS.project
        val sourcesFiles = environmentForJS.getSourceFiles()

        environmentForJS.configuration.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, arguments.allowKotlinPackage)

        if (!checkKotlinPackageUsage(environmentForJS, sourcesFiles)) return ExitCode.COMPILATION_ERROR

        if (arguments.outputFile == null) {
            messageCollector.report(ERROR, "Specify output file via -output", null)
            return ExitCode.COMPILATION_ERROR
        }

        if (messageCollector.hasErrors()) {
            return ExitCode.COMPILATION_ERROR
        }

        if (sourcesFiles.isEmpty() && !IncrementalCompilation.isEnabledForJs()) {
            messageCollector.report(ERROR, "No source files", null)
            return COMPILATION_ERROR
        }

        if (arguments.verbose) {
            reportCompiledSourcesList(messageCollector, sourcesFiles)
        }

        val outputFile = File(arguments.outputFile!!)

        configuration.put(CommonConfigurationKeys.MODULE_NAME, FileUtil.getNameWithoutExtension(outputFile))

        val config = JsConfig(project, configuration)
        val reporter = object : JsConfig.Reporter() {
            override fun error(message: String) {
                messageCollector.report(ERROR, message, null)
            }

            override fun warning(message: String) {
                messageCollector.report(STRONG_WARNING, message, null)
            }
        }
        if (config.checkLibFilesAndReportErrors(reporter)) {
            return COMPILATION_ERROR
        }

        val analyzerWithCompilerReport = AnalyzerWithCompilerReport(
            messageCollector, configuration.languageVersionSettings
        )
        analyzerWithCompilerReport.analyzeAndReport(sourcesFiles) { TopDownAnalyzerFacadeForJS.analyzeFiles(sourcesFiles, config) }
        if (analyzerWithCompilerReport.hasErrors()) {
            return COMPILATION_ERROR
        }

        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        val analysisResult = analyzerWithCompilerReport.analysisResult
        assert(analysisResult is JsAnalysisResult) { "analysisResult should be instance of JsAnalysisResult, but $analysisResult" }
        val jsAnalysisResult = analysisResult as JsAnalysisResult

        var outputPrefixFile: File? = null
        if (arguments.outputPrefix != null) {
            outputPrefixFile = File(arguments.outputPrefix!!)
            if (!outputPrefixFile.exists()) {
                messageCollector.report(ERROR, "Output prefix file '" + arguments.outputPrefix + "' not found", null)
                return ExitCode.COMPILATION_ERROR
            }
        }

        var outputPostfixFile: File? = null
        if (arguments.outputPostfix != null) {
            outputPostfixFile = File(arguments.outputPostfix!!)
            if (!outputPostfixFile.exists()) {
                messageCollector.report(ERROR, "Output postfix file '" + arguments.outputPostfix + "' not found", null)
                return ExitCode.COMPILATION_ERROR
            }
        }

        var outputDir: File? = outputFile.parentFile
        if (outputDir == null) {
            outputDir = outputFile.absoluteFile.parentFile
        }
        try {
            config.configuration.put(JSConfigurationKeys.OUTPUT_DIR, outputDir!!.canonicalFile)
        } catch (e: IOException) {
            messageCollector.report(ERROR, "Could not resolve output directory", null)
            return ExitCode.COMPILATION_ERROR
        }

        if (config.configuration.getBoolean(JSConfigurationKeys.SOURCE_MAP)) {
            checkDuplicateSourceFileNames(messageCollector, sourcesFiles, config)
        }

        val mainCallParameters = createMainCallParameters(arguments.main)
        val translationResult: TranslationResult

        try {

            translationResult = translate(reporter, sourcesFiles, jsAnalysisResult, mainCallParameters, config)
        } catch (e: Exception) {
            throw rethrow(e)
        }

        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        AnalyzerWithCompilerReport.reportDiagnostics(translationResult.diagnostics, messageCollector)

        if (translationResult !is TranslationResult.Success) return ExitCode.COMPILATION_ERROR

        val outputFiles = translationResult.getOutputFiles(outputFile, outputPrefixFile, outputPostfixFile)

        if (outputFile.isDirectory) {
            messageCollector.report(ERROR, "Cannot open output file '" + outputFile.path + "': is a directory", null)
            return ExitCode.COMPILATION_ERROR
        }

        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        outputFiles.writeAll(
            outputDir, messageCollector,
            configuration.getBoolean(CommonConfigurationKeys.REPORT_OUTPUT_FILES)
        )

        return OK
    }

    override fun setupPlatformSpecificArgumentsAndServices(
        configuration: CompilerConfiguration, arguments: K2JSCompilerArguments,
        services: Services
    ) {
        val messageCollector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)

        if (arguments.target != null) {
            assert(arguments.target === "v5") { "Unsupported ECMA version: " + arguments.target!! }
        }
        configuration.put(JSConfigurationKeys.TARGET, EcmaVersion.defaultVersion())

        if (arguments.sourceMap) {
            configuration.put(JSConfigurationKeys.SOURCE_MAP, true)
            if (arguments.sourceMapPrefix != null) {
                configuration.put(JSConfigurationKeys.SOURCE_MAP_PREFIX, arguments.sourceMapPrefix!!)
            }

            var sourceMapSourceRoots = arguments.sourceMapBaseDirs
            if (sourceMapSourceRoots == null && StringUtil.isNotEmpty(arguments.sourceMapPrefix)) {
                sourceMapSourceRoots = calculateSourceMapSourceRoot(messageCollector, arguments)
            }

            if (sourceMapSourceRoots != null) {
                val sourceMapSourceRootList = StringUtil.split(sourceMapSourceRoots, File.pathSeparator)
                configuration.put(JSConfigurationKeys.SOURCE_MAP_SOURCE_ROOTS, sourceMapSourceRootList)
            }
        } else {
            if (arguments.sourceMapPrefix != null) {
                messageCollector.report(WARNING, "source-map-prefix argument has no effect without source map", null)
            }
            if (arguments.sourceMapBaseDirs != null) {
                messageCollector.report(WARNING, "source-map-source-root argument has no effect without source map", null)
            }
        }
        if (arguments.metaInfo) {
            configuration.put(JSConfigurationKeys.META_INFO, true)
        }

        configuration.put(JSConfigurationKeys.TYPED_ARRAYS_ENABLED, arguments.typedArrays)

        configuration.put(JSConfigurationKeys.FRIEND_PATHS_DISABLED, arguments.friendModulesDisabled)

        if (!arguments.friendModulesDisabled && arguments.friendModules != null) {
            val friendPaths = arguments.friendModules!!.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                .filterNot { obj: String -> obj.isEmpty() }
            configuration.put(JSConfigurationKeys.FRIEND_PATHS, friendPaths)
        }

        val moduleKindName = arguments.moduleKind
        var moduleKind: ModuleKind? = if (moduleKindName != null) moduleKindMap[moduleKindName] else ModuleKind.PLAIN
        if (moduleKind == null) {
            messageCollector.report(
                ERROR, "Unknown module kind: $moduleKindName. Valid values are: plain, amd, commonjs, umd", null
            )
            moduleKind = ModuleKind.PLAIN
        }
        configuration.put(JSConfigurationKeys.MODULE_KIND, moduleKind)

        val incrementalDataProvider = services[IncrementalDataProvider::class.java]
        if (incrementalDataProvider != null) {
            configuration.put(JSConfigurationKeys.INCREMENTAL_DATA_PROVIDER, incrementalDataProvider)
        }

        val incrementalResultsConsumer = services[IncrementalResultsConsumer::class.java]
        if (incrementalResultsConsumer != null) {
            configuration.put(JSConfigurationKeys.INCREMENTAL_RESULTS_CONSUMER, incrementalResultsConsumer)
        }

        val lookupTracker = services[LookupTracker::class.java]
        if (lookupTracker != null) {
            configuration.put(CommonConfigurationKeys.LOOKUP_TRACKER, lookupTracker)
        }

        val expectActualTracker = services[ExpectActualTracker::class.java]
        if (expectActualTracker != null) {
            configuration.put(CommonConfigurationKeys.EXPECT_ACTUAL_TRACKER, expectActualTracker)
        }

        val sourceMapEmbedContentString = arguments.sourceMapEmbedSources
        var sourceMapContentEmbedding: SourceMapSourceEmbedding? = if (sourceMapEmbedContentString != null)
            sourceMapContentEmbeddingMap[sourceMapEmbedContentString]
        else
            SourceMapSourceEmbedding.INLINING
        if (sourceMapContentEmbedding == null) {
            val message = "Unknown source map source embedding mode: " + sourceMapEmbedContentString + ". Valid values are: " +
                    StringUtil.join(sourceMapContentEmbeddingMap.keys, ", ")
            messageCollector.report(ERROR, message, null)
            sourceMapContentEmbedding = SourceMapSourceEmbedding.INLINING
        }
        configuration.put(JSConfigurationKeys.SOURCE_MAP_EMBED_SOURCES, sourceMapContentEmbedding)

        if (!arguments.sourceMap && sourceMapEmbedContentString != null) {
            messageCollector.report(WARNING, "source-map-embed-sources argument has no effect without source map", null)
        }
    }

    override fun getPerformanceManager(): CommonCompilerPerformanceManager {
        return performanceManager
    }

    override fun executableScriptFileName(): String {
        return "kotlinc-js"
    }

    private class K2JSCompilerPerformanceManager : CommonCompilerPerformanceManager("Kotlin to JS Compiler")

    companion object {
        private val moduleKindMap = HashMap<String, ModuleKind>()
        private val sourceMapContentEmbeddingMap = LinkedHashMap<String, SourceMapSourceEmbedding>()

        init {
            moduleKindMap[K2JsArgumentConstants.MODULE_PLAIN] = ModuleKind.PLAIN
            moduleKindMap[K2JsArgumentConstants.MODULE_COMMONJS] = ModuleKind.COMMON_JS
            moduleKindMap[K2JsArgumentConstants.MODULE_AMD] = ModuleKind.AMD
            moduleKindMap[K2JsArgumentConstants.MODULE_UMD] = ModuleKind.UMD

            sourceMapContentEmbeddingMap[K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS] = SourceMapSourceEmbedding.ALWAYS
            sourceMapContentEmbeddingMap[K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER] = SourceMapSourceEmbedding.NEVER
            sourceMapContentEmbeddingMap[K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING] = SourceMapSourceEmbedding.INLINING
        }

        @JvmStatic
        fun main(args: Array<String>) {
            CLITool.doMain(K2JSCompiler(), args)
        }

        private fun checkDuplicateSourceFileNames(
            log: MessageCollector,
            sourceFiles: List<KtFile>,
            config: JsConfig
        ) {
            if (config.sourceMapRoots.isEmpty()) return

            val pathResolver = SourceFilePathResolver.create(config)
            val pathMap = HashMap<String, String>()
            val duplicatePaths = HashSet<String>()

            try {
                for (sourceFile in sourceFiles) {
                    val path = sourceFile.virtualFile.path
                    val relativePath = pathResolver.getPathRelativeToSourceRoots(File(sourceFile.virtualFile.path))

                    val existingPath = pathMap[relativePath]
                    if (existingPath != null) {
                        if (duplicatePaths.add(relativePath)) {
                            log.report(
                                WARNING, "There are files with same path '" + relativePath + "', relative to source roots: " +
                                        "'" + path + "' and '" + existingPath + "'. " +
                                        "This will likely cause problems with debugger", null
                            )
                        }
                    } else {
                        pathMap[relativePath] = path
                    }
                }
            } catch (e: IOException) {
                log.report(ERROR, "IO error occurred validating source path:\n" + ExceptionUtil.getThrowableText(e), null)
            }

        }

        private fun reportCompiledSourcesList(messageCollector: MessageCollector, sourceFiles: List<KtFile>) {
            val fileNames = sourceFiles.map { file ->
                val virtualFile = file.virtualFile
                if (virtualFile != null) {
                    return@map MessageUtil.virtualFileToPath(virtualFile)
                }
                file.name + " (no virtual file)"
            }
            messageCollector.report(LOGGING, "Compiling source files: " + join(fileNames, ", "), null)
        }

        private fun configureLibraries(
            arguments: K2JSCompilerArguments,
            paths: KotlinPaths?,
            messageCollector: MessageCollector
        ): List<String> {
            val libraries = SmartList<String>()
            if (!arguments.noStdlib) {
                val stdlibJar = CLICompiler.getLibraryFromHome(
                    paths, { obj: KotlinPaths -> obj.jsStdLibJarPath }, PathUtil.JS_LIB_JAR_NAME, messageCollector, "'-no-stdlib'"
                )
                if (stdlibJar != null) {
                    libraries.add(stdlibJar.absolutePath)
                }
            }

            if (arguments.libraries != null) {
                libraries.addAll(arguments.libraries!!.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().filterNot { obj: String -> obj.isEmpty() })
            }
            return libraries
        }

        private fun calculateSourceMapSourceRoot(
            messageCollector: MessageCollector,
            arguments: K2JSCompilerArguments
        ): String {
            var commonPath: File? = null
            val pathToRoot = ArrayList<File>()
            val pathToRootIndexes = HashMap<File, Int>()

            try {
                for (path in arguments.freeArgs) {
                    var file: File? = File(path).canonicalFile
                    if (commonPath == null) {
                        commonPath = file

                        while (file != null) {
                            pathToRoot.add(file)
                            file = file.parentFile
                        }
                        Collections.reverse(pathToRoot)

                        for (i in pathToRoot.indices) {
                            pathToRootIndexes[pathToRoot[i]] = i
                        }
                    } else {
                        while (file != null) {
                            var existingIndex: Int? = pathToRootIndexes[file]
                            if (existingIndex != null) {
                                existingIndex = Math.min(existingIndex, pathToRoot.size - 1)
                                pathToRoot.subList(existingIndex + 1, pathToRoot.size).clear()
                                commonPath = pathToRoot[pathToRoot.size - 1]
                                break
                            }
                            file = file.parentFile
                        }
                        if (file == null) {
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                val text = ExceptionUtil.getThrowableText(e)
                messageCollector.report(CompilerMessageSeverity.ERROR, "IO error occurred calculating source root:\n$text", null)
                return "."
            }

            return if (commonPath != null) commonPath.path else "."
        }

        private fun createMainCallParameters(main: String?): MainCallParameters {
            return if (K2JsArgumentConstants.NO_CALL == main) {
                MainCallParameters.noCall()
            } else {
                MainCallParameters.mainWithoutArguments()
            }
        }
    }


}
