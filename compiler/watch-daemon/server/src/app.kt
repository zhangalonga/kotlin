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

class WatchDaemon() {
    val compiler = K2JSCompiler()

    lateinit var mySetup: K2JsSetup
    val compilerArgs = K2JSCompilerArguments()

    fun run() {
        println("Initializing...")

        val services = Services.Builder().also {
            it.register(K2JsSetupConsumer::class.java, object : K2JsSetupConsumer {
                override fun consume(setup: K2JsSetup) {
                    mySetup = setup
                }
            })
        }.build()

        compilerArgs.outputFile = "/Users/jetbrains/tasks/jpsmpp/compiler/watch-daemon/testData/test.js"
        val fileName = "/Users/jetbrains/tasks/jpsmpp/compiler/watch-daemon/testData/src/test.kt"
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
            repeat(100) {
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
        time("Updating") {
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

        time("Compiling") {
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

inline fun time(msg: String, body: () -> Unit) {
    val ts = System.nanoTime()
    print("$msg... ")
    body()
    val ms = (System.nanoTime() - ts) / 1000000
    println("Done in $ms ms")
}

fun main(args: Array<String>) {
    WatchDaemon().run()
}