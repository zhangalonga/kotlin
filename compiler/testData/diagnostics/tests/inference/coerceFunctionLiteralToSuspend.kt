// !WITH_NEW_INFERENCE

fun fail1(<!UNUSED_PARAMETER!>c<!>: suspend () -> Unit) {}

fun test() {
    fail1(<!TYPE_MISMATCH!>fun() {}<!>)
    fun fail2(<!UNUSED_PARAMETER!>c<!>: suspend () -> Unit) {}
    fail2(<!TYPE_MISMATCH!>fun() {}<!>)
}