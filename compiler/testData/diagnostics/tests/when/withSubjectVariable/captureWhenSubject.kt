// !LANGUAGE: +VariableDeclarationInWhenSubject
// !DIAGNOSTICS: -UNUSED_VARIABLE

// This is probably more of codegen test
fun `capture when subject`() {
    var y: String = "Before"

    var materializer: (() -> String)? = null

    when (val x = y) {
        "Before" -> materializer = { x }
        else -> return
    }

    y = "After"

    println(materializer!!.invoke()) // What it should print? "Before" or "After"?
}