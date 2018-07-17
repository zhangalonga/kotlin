/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.js

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.HashMap
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants
import org.jetbrains.kotlin.cli.common.checkKotlinPackageUsage
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.js.IncrementalDataProvider
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer
import org.jetbrains.kotlin.js.config.EcmaVersion
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.config.SourceMapSourceEmbedding
import org.jetbrains.kotlin.js.facade.MainCallParameters
import org.jetbrains.kotlin.js.sourceMap.SourceFilePathResolver
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.PathUtil
import org.jetbrains.kotlin.utils.join
import java.io.File
import java.io.IOException
import java.util.*

sealed class K2JsSetup {
    object DoNothing : K2JsSetup()

    class Invalid(
        val exitCode: ExitCode = ExitCode.COMPILATION_ERROR,
        val message: String? = null
    ) : K2JsSetup()

    data class Valid(
        val rootDisposable: Disposable,
        val configuration: CompilerConfiguration,
        val messageCollector: MessageCollector,
        var sourcesFiles: List<KtFile>,
        val config: JsConfig,
        val reporter: JsConfig.Reporter,
        val mainCallParameters: MainCallParameters,
        val outputFile: File,
        val outputPrefixFile: File?,
        val outputPostfixFile: File?,
        val outputDir: File
    ) : K2JsSetup()
}

fun K2JsSetup(
    arguments: K2JSCompilerArguments,
    configuration: CompilerConfiguration,
    rootDisposable: Disposable,
    paths: KotlinPaths?
): K2JsSetup {
    val messageCollector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)

    if (arguments.freeArgs.isEmpty() && !IncrementalCompilation.isEnabledForJs()) {
        if (arguments.version) {
            return K2JsSetup.DoNothing
        }
        messageCollector.report(CompilerMessageSeverity.ERROR, "Specify at least one source file or directory", null)
        return K2JsSetup.Invalid()
    }

    val pluginLoadResult = PluginCliParser.loadPluginsSafe(arguments.pluginClasspaths, arguments.pluginOptions, configuration)
    if (pluginLoadResult != ExitCode.OK) return K2JsSetup.Invalid(pluginLoadResult)

    configuration.put(JSConfigurationKeys.LIBRARIES, configureLibraries(arguments, paths, messageCollector))

    configuration.addKotlinSourceRoots(arguments.freeArgs)
    val environmentForJS =
        KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JS_CONFIG_FILES)

    val project = environmentForJS.project
    val sourcesFiles = environmentForJS.getSourceFiles()

    environmentForJS.configuration.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, arguments.allowKotlinPackage)

    if (!checkKotlinPackageUsage(environmentForJS, sourcesFiles)) return K2JsSetup.Invalid()

    if (arguments.outputFile == null) {
        messageCollector.report(CompilerMessageSeverity.ERROR, "Specify output file via -output", null)
        return K2JsSetup.Invalid()
    }

    if (messageCollector.hasErrors()) {
        return K2JsSetup.Invalid()
    }

    if (sourcesFiles.isEmpty() && !IncrementalCompilation.isEnabledForJs()) {
        messageCollector.report(CompilerMessageSeverity.ERROR, "No source files", null)
        return K2JsSetup.Invalid()
    }

    if (arguments.verbose) {
        reportCompiledSourcesList(messageCollector, sourcesFiles)
    }

    val outputFile = File(arguments.outputFile!!)

    configuration.put(CommonConfigurationKeys.MODULE_NAME, FileUtil.getNameWithoutExtension(outputFile))

    val config = JsConfig(project, configuration)
    val reporter = object : JsConfig.Reporter() {
        override fun error(message: String) {
            messageCollector.report(CompilerMessageSeverity.ERROR, message, null)
        }

        override fun warning(message: String) {
            messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, message, null)
        }
    }
    if (config.checkLibFilesAndReportErrors(reporter)) {
        return K2JsSetup.Invalid()
    }

    var outputPrefixFile: File? = null
    if (arguments.outputPrefix != null) {
        outputPrefixFile = File(arguments.outputPrefix!!)
        if (!outputPrefixFile.exists()) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Output prefix file '" + arguments.outputPrefix + "' not found", null)
            return K2JsSetup.Invalid()
        }
    }

    var outputPostfixFile: File? = null
    if (arguments.outputPostfix != null) {
        outputPostfixFile = File(arguments.outputPostfix!!)
        if (!outputPostfixFile.exists()) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "Output postfix file '" + arguments.outputPostfix + "' not found", null)
            return K2JsSetup.Invalid()
        }
    }

    var outputDir: File? = outputFile.parentFile
    if (outputDir == null) {
        outputDir = outputFile.absoluteFile.parentFile
    }
    try {
        config.configuration.put(JSConfigurationKeys.OUTPUT_DIR, outputDir!!.canonicalFile)
    } catch (e: IOException) {
        messageCollector.report(CompilerMessageSeverity.ERROR, "Could not resolve output directory", null)
        return K2JsSetup.Invalid()
    }

    if (config.configuration.getBoolean(JSConfigurationKeys.SOURCE_MAP)) {
        checkDuplicateSourceFileNames(messageCollector, sourcesFiles, config)
    }

    val mainCallParameters: MainCallParameters = createMainCallParameters(arguments.main)

    return K2JsSetup.Valid(
        rootDisposable,
        configuration,
        messageCollector,
        sourcesFiles,
        config,
        reporter,
        mainCallParameters,
        outputFile,
        outputPrefixFile,
        outputPostfixFile,
        outputDir
    )
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
                        CompilerMessageSeverity.WARNING,
                        "There are files with same path '" + relativePath + "', relative to source roots: " +
                                "'" + path + "' and '" + existingPath + "'. " +
                                "This will likely cause problems with debugger",
                        null
                    )
                }
            } else {
                pathMap[relativePath] = path
            }
        }
    } catch (e: IOException) {
        log.report(CompilerMessageSeverity.ERROR, "IO error occurred validating source path:\n" + ExceptionUtil.getThrowableText(e), null)
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
    messageCollector.report(CompilerMessageSeverity.LOGGING, "Compiling source files: " + join(fileNames, ", "), null)
}

private fun configureLibraries(
    arguments: K2JSCompilerArguments,
    paths: KotlinPaths?,
    messageCollector: MessageCollector
): List<String> {
    val libraries = SmartList<String>()
    if (!arguments.noStdlib) {
        val stdlibJar = CLICompiler.getLibraryFromHome(
            paths,
            { obj: KotlinPaths -> obj.jsStdLibJarPath },
            PathUtil.JS_LIB_JAR_NAME,
            messageCollector,
            "'-no-stdlib'"
        )

        if (stdlibJar != null) libraries.add(stdlibJar.absolutePath)
    }

    arguments.libraries
        ?.split(File.pathSeparator.toRegex())
        ?.filterTo(libraries) { it.isNotEmpty() }

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

fun setupK2JsPlatformSpecificArgumentsAndServices(
    configuration: CompilerConfiguration,
    arguments: K2JSCompilerArguments,
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
            messageCollector.report(CompilerMessageSeverity.WARNING, "source-map-prefix argument has no effect without source map", null)
        }
        if (arguments.sourceMapBaseDirs != null) {
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "source-map-source-root argument has no effect without source map",
                null
            )
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
            CompilerMessageSeverity.ERROR, "Unknown module kind: $moduleKindName. Valid values are: plain, amd, commonjs, umd", null
        )
        moduleKind = ModuleKind.PLAIN
    }
    configuration.put(JSConfigurationKeys.MODULE_KIND, moduleKind)

    val setupConsumer = services[K2JsSetupConsumer::class.java]
    if (setupConsumer != null) {
        configuration.put(JSConfigurationKeys.SETUP_CONSUMER, setupConsumer)
    }

    val setupProvider = services[K2JsSetupProvider::class.java]
    if (setupProvider != null) {
        configuration.put(JSConfigurationKeys.SETUP_PROVIDER, setupProvider)
    }

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
        messageCollector.report(CompilerMessageSeverity.ERROR, message, null)
        sourceMapContentEmbedding = SourceMapSourceEmbedding.INLINING
    }
    configuration.put(JSConfigurationKeys.SOURCE_MAP_EMBED_SOURCES, sourceMapContentEmbedding)

    if (!arguments.sourceMap && sourceMapEmbedContentString != null) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "source-map-embed-sources argument has no effect without source map", null)
    }
}

private val moduleKindMap = mapOf(
    K2JsArgumentConstants.MODULE_PLAIN to ModuleKind.PLAIN,
    K2JsArgumentConstants.MODULE_COMMONJS to ModuleKind.COMMON_JS,
    K2JsArgumentConstants.MODULE_AMD to ModuleKind.AMD,
    K2JsArgumentConstants.MODULE_UMD to ModuleKind.UMD
)

private val sourceMapContentEmbeddingMap = mutableMapOf(
    K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_ALWAYS to SourceMapSourceEmbedding.ALWAYS,
    K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_NEVER to SourceMapSourceEmbedding.NEVER,
    K2JsArgumentConstants.SOURCE_MAP_SOURCE_CONTENT_INLINING to SourceMapSourceEmbedding.INLINING
)