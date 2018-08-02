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

suspend fun getVariableToSpilled() = suspendCoroutineUninterceptedOrReturn<Map<Int, String>> {
    getVariableToSpilledMapping(it, getLabelValue(it))
}

var continuation: Continuation<*>? = null

suspend fun suspendHere() = suspendCoroutineUninterceptedOrReturn<Unit> {
    continuation = it
    COROUTINE_SUSPENDED
}

suspend fun dummy() {}

suspend fun named(): String {
    dummy()
    val s = ""
    return getVariableToSpilled()[1] ?: "named fail"
}

suspend fun suspended() {
    dummy()
    val s = ""
    suspendHere()
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res: String = ""
    builder {
        res = named()
    }
    if (res != "L$0") {
        return "" + res
    }
    builder {
        dummy()
        val a = ""
        res = getVariableToSpilled()[2] ?: "lambda fail"
    }
    if (res != "L$0") {
        return "" + res
    }

    builder {
        suspended()
    }
    res = getVariableToSpilledMapping(continuation!!, getLabelValue(continuation!!))[1] ?: "suspended fail"
    if (res != "L$0") {
        return "" + res
    }
    return "OK"
}