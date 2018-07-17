/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.js

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.containers.HashMap
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.output.outputUtils.writeAll
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils.hasError
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.coroutine.CoroutineTransformer
import org.jetbrains.kotlin.js.facade.K2JSTranslator
import org.jetbrains.kotlin.js.facade.MainCallParameters
import org.jetbrains.kotlin.js.facade.TranslationResult
import org.jetbrains.kotlin.js.facade.TranslationUnit
import org.jetbrains.kotlin.js.facade.exceptions.TranslationException
import org.jetbrains.kotlin.js.inline.JsInliner
import org.jetbrains.kotlin.js.inline.clean.LabeledBlockToDoWhileTransformation
import org.jetbrains.kotlin.js.inline.clean.removeDuplicateImports
import org.jetbrains.kotlin.js.inline.clean.removeUnusedImports
import org.jetbrains.kotlin.js.inline.clean.resolveTemporaryNames
import org.jetbrains.kotlin.js.sourceMap.SourceFilePathResolver
import org.jetbrains.kotlin.js.translate.general.Translation
import org.jetbrains.kotlin.js.translate.utils.expandIsCalls
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.js.KotlinJavascriptSerializationUtil
import org.jetbrains.kotlin.serialization.js.ast.JsAstSerializer
import org.jetbrains.kotlin.utils.rethrow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*

@Throws(TranslationException::class)
fun translate(
    reporter: JsConfig.Reporter,
    allKotlinFiles: List<KtFile>,
    jsAnalysisResult: JsAnalysisResult,
    mainCallParameters: MainCallParameters,
    config: JsConfig
): TranslationResult {
    val translator = K2JSTranslator(config)
    val incrementalDataProvider = config.configuration.get(JSConfigurationKeys.INCREMENTAL_DATA_PROVIDER)
    if (incrementalDataProvider != null) {
        val consumer = config.configuration[JSConfigurationKeys.TRANSLATION_UNIT_CONSUMER]
        if (true) {
            return translateIsolatedUnit(
                allKotlinFiles.map { TranslationUnit.SourceFile(it) },
                mainCallParameters,
                jsAnalysisResult,
                reporter,
                config
            )
        } else {
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
    }

    allKotlinFiles.sortedBy { ktFile -> VfsUtilCore.virtualToIoFile(ktFile.virtualFile) }
    return translator.translate(reporter, allKotlinFiles, mainCallParameters, jsAnalysisResult)
}

fun translateIsolatedUnit(
    sources: List<TranslationUnit.SourceFile>,
    mainCallParameters: MainCallParameters,
    analysisResult: JsAnalysisResult,
    reporter: JsConfig.Reporter,
    config: JsConfig
): TranslationResult {
    val files = ArrayList<KtFile>()

    sources.forEach {
        files.add(it.file)
    }

    val bindingTrace = analysisResult.bindingTrace
    TopDownAnalyzerFacadeForJS.checkForErrors(files, bindingTrace.getBindingContext())
    val moduleDescriptor = analysisResult.moduleDescriptor
    val diagnostics = bindingTrace.bindingContext.diagnostics

    val pathResolver = SourceFilePathResolver.create(config)

    val translationResult = Translation.generateAst(
        bindingTrace, sources, mainCallParameters, moduleDescriptor, config, pathResolver
    )
    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    if (hasError(diagnostics)) return TranslationResult.Fail(diagnostics)

    val newFragments = ArrayList(translationResult.newFragments)
    val allFragments = ArrayList(translationResult.fragments)

    JsInliner.process(
        reporter, config, analysisResult.bindingTrace, translationResult.innerModuleName,
        allFragments, newFragments, translationResult.importStatements
    )

    LabeledBlockToDoWhileTransformation.apply(newFragments)

    val coroutineTransformer = CoroutineTransformer()
    for (fragment in newFragments) {
        coroutineTransformer.accept(fragment.declarationBlock)
        coroutineTransformer.accept(fragment.initializerBlock)
    }

    removeUnusedImports(translationResult.program)
    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
    if (hasError(diagnostics)) return TranslationResult.Fail(diagnostics)

    expandIsCalls(newFragments)
    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
    val serializer = JsAstSerializer { file ->
        try {
            pathResolver.getPathRelativeToSourceRoots(file)
        } catch (e: IOException) {
            throw RuntimeException("IO error occurred resolving path to source file", e)
        }
    }

    val incrementalResults = config.configuration.get(JSConfigurationKeys.INCREMENTAL_RESULTS_CONSUMER)
    if (incrementalResults != null) {
        val serializationUtil = KotlinJavascriptSerializationUtil

        for (file in files) {
            val fragment = translationResult.fragmentMap[file] ?: error("Could not find AST for file: $file")
            val output = ByteArrayOutputStream()
            serializer.serialize(fragment, output)
            val binaryAst = output.toByteArray()

            val scope = translationResult.fileMemberScopes[file] ?: error("Could not find descriptors for file: $file")
            val packagePart = serializationUtil.serializeDescriptors(
                bindingTrace.bindingContext, moduleDescriptor, scope, file.packageFqName,
                config.configuration.languageVersionSettings
            )

            val ioFile = VfsUtilCore.virtualToIoFile(file.virtualFile)
            incrementalResults.processPackagePart(ioFile, packagePart.toByteArray(), binaryAst)
        }

        val settings = config.configuration.languageVersionSettings
        incrementalResults.processHeader(serializationUtil.serializeHeader(moduleDescriptor, null, settings).toByteArray())
    }

    removeDuplicateImports(translationResult.program)
    translationResult.program.resolveTemporaryNames()
    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
    if (hasError(diagnostics)) return TranslationResult.Fail(diagnostics)

    val importedModules = ArrayList<String>()
    for (module in translationResult.importedModuleList) {
        importedModules.add(module.externalName)
    }

    return TranslationResult.Success(
        config, files, translationResult.program, diagnostics, importedModules,
        moduleDescriptor, bindingTrace.getBindingContext()
    )
}

fun K2JsSetup.Valid.doExecute(): ExitCode {
    val analyzerWithCompilerReport = AnalyzerWithCompilerReport(messageCollector, configuration.languageVersionSettings)
    analyzerWithCompilerReport.analyzeAndReport(sourcesFiles) { TopDownAnalyzerFacadeForJS.analyzeFiles(sourcesFiles, config) }
    if (analyzerWithCompilerReport.hasErrors()) {
        return ExitCode.COMPILATION_ERROR
    }

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    val analysisResult = analyzerWithCompilerReport.analysisResult
    assert(analysisResult is JsAnalysisResult) { "analysisResult should be instance of JsAnalysisResult, but $analysisResult" }
    val jsAnalysisResult = analysisResult as JsAnalysisResult

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    val translationResult = try {
        translate(reporter, sourcesFiles, jsAnalysisResult, mainCallParameters, config)
    } catch (e: Exception) {
        throw rethrow(e)
    }

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    AnalyzerWithCompilerReport.reportDiagnostics(translationResult.diagnostics, messageCollector)

    if (translationResult !is TranslationResult.Success) return ExitCode.COMPILATION_ERROR

    val outputFiles = translationResult.getOutputFiles(outputFile, outputPrefixFile, outputPostfixFile)

    if (outputFile.isDirectory) {
        messageCollector.report(CompilerMessageSeverity.ERROR, "Cannot open output file '" + outputFile.path + "': is a directory", null)
        return ExitCode.COMPILATION_ERROR
    }

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    outputFiles.writeAll(
        outputDir,
        messageCollector,
        configuration.getBoolean(CommonConfigurationKeys.REPORT_OUTPUT_FILES)
    )

    return ExitCode.OK
}
