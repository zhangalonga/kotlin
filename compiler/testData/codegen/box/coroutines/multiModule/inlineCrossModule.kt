// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND: NATIVE
// WITH_COROUTINES
// WITH_RUNTIME
// COMMON_COROUTINES_TEST

// MODULE: lib(support)
// FILE: lib.kt

import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

suspend inline fun <R> inlined(
    crossinline step: suspend () -> R
): R = notInlined { step() }

suspend fun <R> notInlined(
    block: suspend () -> R
): R = block()

// MODULE: main(lib)
// FILE: main.kt
// WITH_COROUTINES
// WITH_RUNTIME
// COMMON_COROUTINES_TEST

import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

var result = "FAIL"

suspend fun test() {
    inlined {
        result = "OK"
    }
}

open class EmptyContinuation(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<Any?> {
    companion object : EmptyContinuation()
    override fun resume(data: Any?) {}
    override fun resumeWithException(exception: Throwable) { throw exception }
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    builder {
        test()
    }
    return result
}