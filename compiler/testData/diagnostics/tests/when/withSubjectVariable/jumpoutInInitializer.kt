// !LANGUAGE: +VariableDeclarationInWhenSubject
// !DIAGNOSTICS: -UNUSED_VARIABLE


fun `jump out in elvis`(x: Int?) {
    loop@ while (true) {
        val y = when (val z = x ?: break@loop) {
            0 -> "0"
            else -> "not 0"
        }

        <!DEBUG_INFO_SMARTCAST!>x<!>.inc() // Should be OK
    }

    x<!UNSAFE_CALL!>.<!>inc() // Should be error
}

fun `jump out in elvis-like if`(x: Int?) {
    loop@ while (true) {
        val y = when (val z = if (x == null) break@loop else <!DEBUG_INFO_SMARTCAST!>x<!>) {
            0 -> "0"
            else -> "not 0"
        }
        <!DEBUG_INFO_SMARTCAST!>x<!>.inc() // Should be OK
    }

    x<!UNSAFE_CALL!>.<!>inc() // Should be error
}


fun getBoolean() = true
fun `jump out in if`(x: Int?) {
    loop@ while (true) {
        val y = when (val z = if (getBoolean()) { x!!; break@loop } else x) {
            0 -> "0"
            else -> "not 0"
        }
        x<!UNSAFE_CALL!>.<!>inc() // Should be error
    }

    x<!UNSAFE_CALL!>.<!>inc() // Actually, safe, but it's OK if it's error
}