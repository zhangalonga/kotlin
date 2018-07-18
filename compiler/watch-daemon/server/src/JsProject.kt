/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon

import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JsSetup
import org.jetbrains.kotlin.incremental.IncrementalCachesManager
import org.jetbrains.kotlin.incremental.IncrementalJsCachesManager
import org.jetbrains.kotlin.incremental.js.IncrementalDataProviderFromCache
import org.jetbrains.kotlin.js.resolve.JsPlatform
import java.io.File

class JsProject(
    val order: Int,
    val sourceRoots: List<File>,
    val compilerArguments: K2JSCompilerArguments
) {
    val name = File(compilerArguments.outputFile).nameWithoutExtension
    val usages = mutableListOf<JsProject>()
    val deps = mutableListOf<JsProject>()
    val libs = mutableListOf<File>()

    var setup: K2JsSetup? = null
    var incrementalDataProvider: IncrementalDataProviderFromCache? = null
    var cacheManager: IncrementalJsCachesManager? = null

    override fun toString() = "JsProject($name)"
}

fun K2JSCompilerArguments.detectJsProject(order: Int): JsProject {
    val sourceRoots = mutableSetOf<File>()

    freeArgs.forEach {
        var file = File(it)
        check(file.isFile)

        while (true) {
            file = file.parentFile
            check(file.isDirectory)
            if (file.isDirectory && file.name in setOf("src", "generated-src")) {
                break
            }
        }

        sourceRoots.add(file)
    }


    K2JSCompilerArguments()
    this.freeArgs = listOf()

    return JsProject(order, sourceRoots.toList(), this)
}