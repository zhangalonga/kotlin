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
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.js.K2JsSetup
import org.jetbrains.kotlin.cli.js.K2JsSetupConsumer
import org.jetbrains.kotlin.cli.js.doExecute
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.swing.SwingUtilities

// Done in 139598 ms
// Done in 92707 ms

class WatchDaemon() {
    val compiler = K2JSCompiler()

    val compilerArgs = K2JSCompilerArguments()

    fun run2() {
        val moduleArgs = time("loadCircletModules") { loadCircletModules() }

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

                setUps.forEach {
                    it as K2JsSetup.Valid
                    compile(it.copy(
                        outputDir = outputDir,
                        outputFile = File(outputDir, it.outputFile.name)
                    ))
                }
            }
        }
    }

    fun loadCircletModules(): List<K2JSCompilerArguments> {
        return File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/args_backup.txt")
            .readLines()
            .map { line ->
                val args = line.split(",").map { it.trim() }.map {
                    check(it.startsWith("\"")) { it }
                    check(it.endsWith("\"")) { it }
                    it.substring(1, it.length - 1)
                }

                K2JSCompilerArguments().also { parseCommandLineArguments(args, it) }
            }
    }

    fun run() {
        println("Initializing...")

        lateinit var mySetup: K2JsSetup
        val services = Services.Builder().also {
            it.register(K2JsSetupConsumer::class.java, object : K2JsSetupConsumer {
                override fun consume(setup: K2JsSetup) {
                    mySetup = setup
                }
            })
        }.build()

        val testData = "/Users/jetbrains/kotlin/compiler/watch-daemon/testData"
        compilerArgs.outputFile = "$testData/test.js"
        val fileName = "$testData/src/test.kt"
        compilerArgs.freeArgs = listOf(fileName)

        println("Setup...")

        compiler.exec(
            PrintingMessageCollector(
                System.out,
                MessageRenderer.PLAIN_RELATIVE_PATHS,
                true
            ),
            services,
            compilerArgs
        )

        val myFinalSetup = mySetup
        if (myFinalSetup is K2JsSetup.Valid) {
            val file = File(fileName)

            // initial compilation
            repeat(3) {
                print("Warning up... [$it%] :")
                compile(myFinalSetup)
            }

            var m = file.lastModified()
            while (true) {
                val m1 = file.lastModified()
                if (m1 != m) {
                    m = m1
                    compile(myFinalSetup)
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
    WatchDaemon().run2()
}