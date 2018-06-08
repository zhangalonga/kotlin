// !LANGUAGE: +VariableDeclarationInWhenSubject
// !DIAGNOSTICS: -UNUSED_VARIABLE


fun `no subject variable name`(x: Int?) {
    val y = when (val = 42) {
        0 -> "0"
        else -> "not 0"
    }
}