fun foo(c: () -> Unit):Int = 1

fun test() {
    fun foo(c: suspend () -> Unit):String = ""
    val s: String = foo(fun (){})
}