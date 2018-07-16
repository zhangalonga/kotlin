/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.impl.FileManagerImpl
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.js.K2JsSetup
import org.jetbrains.kotlin.cli.js.K2JsSetupConsumer
import org.jetbrains.kotlin.cli.js.doExecute
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.swing.SwingUtilities

// Done in 139598 ms
// Done in 92707 ms

class WatchDaemon() {
    val compiler = K2JSCompiler()
    val workingDir = File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/caches")
    val msgs = PrintingMessageCollector(
        System.out,
        MessageRenderer.PLAIN_RELATIVE_PATHS,
        false//true
    )


    fun runIncremental() {
        val jsProjects = loadCircletProjects()

        val fileWatcher = FileWatcher(jsProjects.flatMap { project ->
            project.sourceRoots.map {
                FileWatchRoot(it, project)
            }
        })

        fileWatcher.start()
        fileWatcher.takeDirtyRoots()

        while (true) {
            fileWatcher.markDirty(File("/Users/jetbrains/circlet/app/app-web/src/main/kotlin/circlet/api/Routing.kt"))
            processDirty(fileWatcher.takeDirtyRoots())
        }

//        watch(fileWatcher)
    }

    fun watch(fileWatcher: FileWatcher<JsProject>) {
        watchloop@ while (true) {
            val dirtyRoots = fileWatcher.pollDirtyRoots()
            processDirty(dirtyRoots)
        }
    }

    fun processDirty(dirtyRoots: List<DirtyRoot<JsProject>>) {
        val dirtyProjects = dirtyRoots.filter { !it.isInitial }.groupBy { it.root.data }
        if (dirtyProjects.isNotEmpty()) {
            val sorted = dirtyProjects.entries
                .sortedBy { (project, _) -> project.order }

            sorted.forEach { (project, dirty) ->
                time("$dirty") {
                    val allFiles = mutableListOf<File>()
                    val modified = mutableSetOf<File>()

                    dirty.forEach {
                        allFiles.addAll(it.all)
                        modified.addAll(it.modified)
                    }

                    val exitCode = execJsIncrementalCompiler(
                        allFiles,
//                        ChangedFiles.Unknown(),
                        ChangedFiles.Known(modified.toList(), listOf()),
                        File(workingDir, project.name),
                        project.compilerArguments,
                        msgs
                    )

                    if (exitCode.code != 0) {
                        error(exitCode)
                    }
                }
            }
        }
    }

    fun loadCircletProjects(): List<JsProject> {
        val executionsCmdArgs = loadCircletExecutionsCmdArgs()

        val jsProjects = executionsCmdArgs.mapIndexed { index: Int, module: K2JSCompilerArguments ->
            module.detectJsProject(index)
        }

        val metaJsToProject = mutableMapOf<String, JsProject>()
        jsProjects.forEach {
            val outputFile = it.compilerArguments.outputFile!!
            check(outputFile.endsWith(".js"))
            val metaJs = outputFile.substring(0, outputFile.length - ".js".length) + ".meta.js"
            metaJsToProject[metaJs] = it
        }

        jsProjects.forEach { src ->
            val libs = src.compilerArguments.libraries?.split(':') ?: listOf()
            libs.forEach { lib ->
                val jsProject = metaJsToProject[lib]
                if (jsProject != null) {
                    src.deps.add(jsProject)
                    jsProject.usages.add(src)
                } else {
                    check(!lib.endsWith(".meta.js"))
                    val libFile = File(lib)
                    check(libFile.exists())
                    src.libs.add(libFile)
                }
            }
        }

        return jsProjects
    }

    private fun execJsIncrementalCompiler(
        allKotlinFiles: List<File>,
        changedFiles: ChangedFiles,
        workingDir: File,
        args: K2JSCompilerArguments,
        compilerMessageCollector: MessageCollector
    ): ExitCode {
        val reporter = object : ICReporter {
            override fun report(message: () -> String) {
                println(message())
            }
        }

        val versions = commonCacheVersions(workingDir)
        val compiler = IncrementalJsCompilerRunner(workingDir, versions, reporter)
        return compiler.compile(allKotlinFiles, args, compilerMessageCollector, changedFiles)
    }

    fun runRebuild() {
        val moduleArgs = time("loadCircletExecutionsCmdArgs") { loadCircletExecutionsCmdArgs() }

        val setUps = time("Initializing...") {
            moduleArgs.map { args ->
                lateinit var mySetup: K2JsSetup
                val services = Services.Builder().also {
                    it.register(K2JsSetupConsumer::class.java, object : K2JsSetupConsumer {
                        override fun consume(setup: K2JsSetup) {
                            mySetup = setup
                        }
                    })
                }.build()

                compiler.exec(
                    PrintingMessageCollector(
                        System.out,
                        MessageRenderer.PLAIN_RELATIVE_PATHS,
                        false//true
                    ),
                    services,
                    args
                )

                mySetup
            }
        }

        (1..10).forEach {
            File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/reports/all.txt").appendText(
                "------------ $it ------------\n"
            )

            time("Compiling all...") {
                val outputDir = File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/output")

                setUps.forEach { setup ->
                    setup as K2JsSetup.Valid

                    compile(
                        setup.copy(
                            outputDir = outputDir,
                            outputFile = File(outputDir, setup.outputFile.name)
                        )
                    )
                }
            }
        }
    }

    private fun compile(myFinalSetup: K2JsSetup.Valid) {
        time("Updating ${myFinalSetup.outputFile.name}") {
            SwingUtilities.invokeAndWait {
                myFinalSetup.sourcesFiles = myFinalSetup.sourcesFiles.map {
                    val virtualFile = it.virtualFile
                    val project = it.project
                    virtualFile.refresh(false, true)
                    val psiManagerEx = PsiManagerEx.getInstanceEx(project) as PsiManagerImpl
                    val fileManager = psiManagerEx.fileManager as FileManagerImpl
                    psiManagerEx.dropPsiCaches()
                    PsiDocumentManager.getInstance(project).getDocument(it)!!.setText(LoadTextUtil.loadText(virtualFile))
                    fileManager.setViewProvider(virtualFile, null)
                    val b = PsiManager.getInstance(project).findFile(virtualFile) as KtFile
                    b
                }
            }
        }

        time("Compiling ${myFinalSetup.outputFile.name}") {
            myFinalSetup.doExecute()
            (myFinalSetup.messageCollector as GroupingMessageCollector).flush()
        }

//        time("Cold compiling") {
//            compiler.exec(
//                PrintingMessageCollector(
//                    System.out,
//                    MessageRenderer.PLAIN_RELATIVE_PATHS,
//                    true
//                ),
//                Services.Builder().build(),
//                compilerArgs
//            )
//        }
    }
}

inline fun <R> time(msg: String, body: () -> R): R {
    val ts = System.nanoTime()
    println("vvvvvvvvvvvvvvvvv $msg vvvvvvvvvvvvvvvvv")
    val result = body()
    val ms = (System.nanoTime() - ts) / 1000000
    val mem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L)
    println("^^^^^^^^^^^^ Done in $ms ms ^^^^^^^^^^^^ memory: $mem Mb")

    File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/reports/all.txt").appendText(
        "$msg: $ms ms, $mem Mb\n"
    )

    return result
}

fun main(args: Array<String>) {
//    WatchDaemon().runRebuild()
    WatchDaemon().runIncremental()
}