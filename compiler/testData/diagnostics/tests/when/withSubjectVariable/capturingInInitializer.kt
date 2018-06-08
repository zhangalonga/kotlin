// !LANGUAGE: +VariableDeclarationInWhenSubject
// !DIAGNOSTICS: -UNUSED_VARIABLE

fun getBoolean() = true

fun `safe capture var in initializer`() {
    var x: Int? = 42
    x!!
    <!DEBUG_INFO_SMARTCAST!>x<!>.inc() // OK

    val s = when (val y = run { x = 42; 32 }) {
        0 -> {
            <!SMARTCAST_IMPOSSIBLE!>x<!>.inc() // currently not OK
            "0"
        }
        else -> "!= 0"
    }

    <!SMARTCAST_IMPOSSIBLE!>x<!>.inc() // currently not OK
}


fun `unsafe capture var in initializer`() {
    var x: Int? = 42
    x!!
    <!DEBUG_INFO_SMARTCAST!>x<!>.inc() // OK

    val s = when (val y = run { x = null; 32 }) {
        0 -> {
            <!SMARTCAST_IMPOSSIBLE!>x<!>.inc() // absolutely not ok
            "0"
        }
        else -> "!= 0"
    }

    <!SMARTCAST_IMPOSSIBLE!>x<!>.inc() // absolutely not OK
}