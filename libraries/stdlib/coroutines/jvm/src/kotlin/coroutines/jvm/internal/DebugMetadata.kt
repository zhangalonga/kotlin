/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.jvm.internal

import kotlin.coroutines.Continuation

// TODO: Uncomment when KT-25372 is fixed
@Target(AnnotationTarget.CLASS)
annotation class DebugMetadata(
    // @JvmName("a")
    val runtimeSourceFiles: Array<String>,
    // @JvmName("b")
    val runtimeLineNumbers: IntArray,
    // @JvmName("c")
    val debugLocalIndexes: IntArray,
    // @JvmName("d")
    val debugSpilled: Array<String>,
    // @JvmName("e")
    val debugIndexToLabel: IntArray
)

// TODO: move to appropriate place
public fun getSourceFileAndLineNumber(c: Continuation<*>, label: Int): Pair<String, Int> {
    val debugMetadata = c.getDebugMetadataAnnotation()
    return debugMetadata.runtimeSourceFiles.zip(debugMetadata.runtimeLineNumbers.asList())[label]
}

private fun Continuation<*>.getDebugMetadataAnnotation(): DebugMetadata {
    this as BaseContinuationImpl
    return javaClass.annotations.filterIsInstance<DebugMetadata>()[0]
}

public fun getVariableToSpilledMapping(c: Continuation<*>, label: Int): Map<Int, String> {
    val debugMetadata = c.getDebugMetadataAnnotation()
    val res = hashMapOf<Int, String>()
    for ((i, labelOfIndex) in debugMetadata.debugIndexToLabel.withIndex()) {
        if (labelOfIndex == label) {
            res[debugMetadata.debugLocalIndexes[i]] = debugMetadata.debugSpilled[i]
        }
    }
    return res
}