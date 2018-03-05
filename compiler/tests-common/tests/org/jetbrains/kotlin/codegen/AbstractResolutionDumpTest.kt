/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.cli.jvm.compiler.tmp.IrBasedResolutionDumpBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.tmp.RDumpForFile
import org.jetbrains.kotlin.ir.AbstractIrGeneratorTestCase
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import java.io.File

class AbstractResolutionDumpTest : AbstractIrGeneratorTestCase() {
    override fun doTest(wholeFile: File, testFiles: List<TestFile>) {
        val analysisResult = JvmResolveUtil.analyze(ktFilesToAnalyze, environment)
        val irModule = generateIrModule(ignoreErrors = true)

        for (irFile in irModule.files) {
            val resolutionDump: RDumpForFile = IrBasedResolutionDumpBuilder()
            val serializedResolutionResultsInFile =
        }
    }
}