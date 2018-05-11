// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

suspend fun suspendHere(): Int = suspendCoroutine<Int> {
    it.resume(42)
}

suspend fun calculate(b: Boolean): Int {
    return if (b) {
        1
    } else {
        suspendHere()
    }
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res = 0
    builder {
        res = calculate(false)
    }
    if (res != 42) return "FAIL $res"
    return "OK"
}
