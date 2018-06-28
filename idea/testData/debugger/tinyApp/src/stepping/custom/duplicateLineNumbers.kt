package duplicateLineNumbers

fun main(args: Array<String>) {
    //Breakpoint!
    finallyBlock()
    finallyBlockAsInline()
    inlineNoInline()
    inlineNoInlineAsInline()
}

fun finallyBlock() {
    try {
        val a = 1
    } finally {
        val a = 1
    }

    try {
        try {
            throw RuntimeException()
        } finally {
            val a = 1
        }
    } catch (e: RuntimeException) {
        val a = 1
    } finally {
        val a = 1
    }
}

inline fun finallyBlockAsInline() {
    try {
        val a = 1
    } finally {
        val a = 1
    }

    try {
        try {
            throw RuntimeException()
        } finally {
            val a = 1
        }
    } catch (e: RuntimeException) {
        val a = 1
    } finally {
        val a = 1
    }
}

fun inlineNoInline() {
    12.inlineExt().normalExt()
}

fun inlineNoInlineAsInline() {
    12.inlineExt().normalExt()
}

inline fun Any.inlineExt(): Any = this
fun Any.normalExt(): Any = this

// STEP_INTO: 1
// STEP_OVER: 14
// STEP_INTO: 1
// STEP_OVER: 13
// STEP_INTO: 1
// STEP_OVER: 2
// STEP_INTO: 1
// STEP_OVER: 2
// STEP_INTO: 1
// STEP_OVER: 2