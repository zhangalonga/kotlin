// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

fun baz(i: Int) {}

suspend fun bol() = true
suspend fun bal() = true
fun bil() = true

suspend fun foo() = 42

suspend fun bar(b: Boolean) {
    baz(0)
    L@while (bol()) {
        do {
            if (b) {
                val v = foo()
                baz(v)
                if (bol()) continue
                baz(foo())
                if (bal()) break@L
            } else {
                val v = foo()
                baz(v)
                if (bol()) break
                foo()
                if (bil()) continue@L
            }
            baz(foo())
        } while (bal())
    }
    var v = foo()
    baz(v)
    v = foo()
    baz(v)
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
