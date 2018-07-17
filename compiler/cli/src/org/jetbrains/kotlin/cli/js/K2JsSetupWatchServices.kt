/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.js

interface K2JsSetupConsumer {
    fun consume(setup: K2JsSetup)
}

interface K2JsSetupProvider {
    fun provide(): K2JsSetup
}

interface K2JsResultsConsumer {
    fun consume()
}