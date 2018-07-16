/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon

import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import java.io.File

fun loadCircletExecutionsCmdArgs(): List<K2JSCompilerArguments> {
    return File("/Users/jetbrains/kotlin/compiler/watch-daemon/testData/args_backup.txt")
        .readLines()
        .map { line ->
            val args = line.split(",").map { it.trim() }.map {
                check(it.startsWith("\"")) { it }
                check(it.endsWith("\"")) { it }
                it.substring(1, it.length - 1)
            }

            K2JSCompilerArguments().also {
                parseCommandLineArguments(args, it)
//                    it.newInference = true
            }
        }
}