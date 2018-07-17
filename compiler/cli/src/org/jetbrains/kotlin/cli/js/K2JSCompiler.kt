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
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CLITool
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.utils.KotlinPaths

class K2JSCompiler : CLICompiler<K2JSCompilerArguments>() {
    private val performanceManager = K2JSCompilerPerformanceManager()

    override fun createArguments(): K2JSCompilerArguments {
        return K2JSCompilerArguments()
    }

    override fun doExecute(
        arguments: K2JSCompilerArguments,
        configuration: CompilerConfiguration,
        rootDisposable: Disposable,
        paths: KotlinPaths?
    ): ExitCode {
        val setupProvider = configuration[JSConfigurationKeys.SETUP_PROVIDER]
        
        val setup = if (setupProvider == null) {
            val setup = K2JsSetup(arguments, configuration, rootDisposable, paths)

            val setupConsumer = configuration[JSConfigurationKeys.SETUP_CONSUMER]
            if (setupConsumer != null) (setupConsumer as K2JsSetupConsumer).consume(setup)

            setup
        } else (setupProvider as K2JsSetupProvider).provide()

        return when (setup) {
            is K2JsSetup.DoNothing -> ExitCode.OK
            is K2JsSetup.Invalid -> setup.exitCode
            is K2JsSetup.Valid -> setup.doExecute()
        }
    }

    override fun setupPlatformSpecificArgumentsAndServices(
        configuration: CompilerConfiguration,
        arguments: K2JSCompilerArguments,
        services: Services
    ) {
        setupK2JsPlatformSpecificArgumentsAndServices(configuration, arguments, services)
    }

    override fun getPerformanceManager(): CommonCompilerPerformanceManager {
        return performanceManager
    }

    override fun executableScriptFileName(): String {
        return "kotlinc-js"
    }

    private class K2JSCompilerPerformanceManager : CommonCompilerPerformanceManager("Kotlin to JS Compiler")

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CLITool.doMain(K2JSCompiler(), args)
        }
    }
}
