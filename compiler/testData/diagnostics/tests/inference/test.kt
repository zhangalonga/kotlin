fun foo(c: () -> Unit) {}

fun test() {
    fun foo(c: suspend () -> Unit) {}
    foo(fun (){})
}