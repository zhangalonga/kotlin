// IGNORE_BACKEND: JS_IR
// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

fun baz(i: Int) {}

suspend fun bol() = true

suspend fun foo() = 42

suspend fun bar(b: Boolean) {
//    baz(0)
//    if (b) {
//        val v = foo()
//        baz(v)
//    } else {
//        val v = foo()
//        baz(v)
//        foo()
//    }
    baz(2)
    var v = foo()
    baz(v)
//    v = foo()
//    baz(v)
}

//suspend fun suspendHere(): String = suspendCoroutineOrReturn { x ->
//    x.resume("OK")
//    COROUTINE_SUSPENDED
//}
//
//fun builder(c: suspend () -> Unit) {
//    c.startCoroutine(EmptyContinuation)
//}

fun box(): String {
    return "OK"
//    var result = ""
//
//    builder {
//        result = suspendHere()
//    }
//
//    return result
}
