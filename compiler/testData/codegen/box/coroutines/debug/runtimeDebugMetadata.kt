// TODO:
// LANGUAGE_VERSION: 1.3

// TARGET_BACKEND: JVM
// WITH_RUNTIME
// WITH_COROUTINES

import helpers.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.jvm.internal.*

fun getLabelValue(c: Continuation<*>): Int {
    val field = c.javaClass.getDeclaredField("label")
    field.setAccessible(true)
    return field.get(c) as Int - 1
}

suspend fun getSourceFileAndLineNumberFromContinuation() = suspendCoroutineUninterceptedOrReturn<Pair<String, Int>> {
    getSourceFileAndLineNumber(it, getLabelValue(it))
}

suspend fun dummy() {}

suspend fun named(): Pair<String, Int> {
    dummy()
    return getSourceFileAndLineNumberFromContinuation()
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res: Any? = null
    builder {
        res = named()
    }
    if (res != Pair("runtimeDebugMetadata.kt", 27)) {
        return "" + res
    }
    builder {
        dummy()
        res = getSourceFileAndLineNumberFromContinuation()
    }
    if (res != Pair("runtimeDebugMetadata.kt", 44)) {
        return "" + res
    }
    return "OK"
}