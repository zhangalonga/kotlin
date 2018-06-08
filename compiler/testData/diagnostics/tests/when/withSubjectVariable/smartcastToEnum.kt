// !LANGUAGE: +VariableDeclarationInWhenSubject
// !DIAGNOSTICS: -UNUSED_VARIABLE

enum class E { FIRST, SECOND }

fun `smartcast to enum in subject initializer`(e: E?) {
    val x = when (val ne = e!!) {
        E.FIRST -> "f"
        E.SECOND -> "s"
    }
}


sealed class Either
class Left : Either()
class Right : Either()

fun `smartcast to sealed in subject initializer`(x: Any?) {
    val y = when (val either = x as Either) {
        is Left -> "L"
        is Right -> "R"
    }
}