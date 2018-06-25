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

fun aaa(a: Any) {}

suspend fun bar(b: Boolean) {


    try {

        if (bal()) {
            bar(bol())
        } else {
            baz(foo())
        }
    } catch (e: Exception) {
        aaa(e)
    }

//    try {
//        if (b) {
//            baz(1)
//            baz(foo())
//            baz(2)
//        }
//    } catch (ex: Exception) {
//        if (bil()) {
//            baz(3)
//        } else{
//            baz(foo())
//        }
//    } finally {
//        baz(foo())
//    }

//    baz(0)
//    L@while (bol()) {
//        do {
//            if (b) {
//                val v = foo()
//                baz(v)
//                if (bol()) continue
//                baz(foo())
//                if (bal()) break@L
//            } else {
//                baz(2)
//                val v = foo()
//                baz(v)
//                if (bol()) break
//                foo()
//                if (bil()) continue@L
//            }
//            baz(foo())
//        } while (bal())
//    }
//    var v = foo()
//    baz(v)
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
