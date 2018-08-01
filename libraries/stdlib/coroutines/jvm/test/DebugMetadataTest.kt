@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.jvm.internal.*
import kotlin.test.assertEquals

/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

class DebugMetadataTest {
    @Test
    fun testRuntimeDebugMetadata() {
        val myContinuation = @DebugMetadata(
            runtimeSourceFiles = ["test.kt", "test1.kt", "test.kt"],
            runtimeLineNumbers = [10, 2, 11],
            debugIndexToLabel = [0, 0, 1, 1, 2],
            debugLocalIndexes = [1, 2, 2, 3, 3],
            debugSpilled = ["L$1", "L$2", "L$1", "L$2", "L$1"]
        ) object : BaseContinuationImpl(null) {
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            override fun invokeSuspend(result: SuccessOrFailure<Any?>): Any? = null
        }

        assertEquals("test.kt" to 10, getSourceFileAndLineNumber(myContinuation, 0))
        assertEquals("test1.kt" to 2, getSourceFileAndLineNumber(myContinuation, 1))
        assertEquals("test.kt" to 11, getSourceFileAndLineNumber(myContinuation, 2))

        assertEquals(mapOf(1 to "L$1", 2 to "L$2"), getVariableToSpilledMapping(myContinuation, 0))
        assertEquals(mapOf(2 to "L$1", 3 to "L$2"), getVariableToSpilledMapping(myContinuation, 1))
        assertEquals(mapOf(3 to "L$1"), getVariableToSpilledMapping(myContinuation, 2))
    }
}
