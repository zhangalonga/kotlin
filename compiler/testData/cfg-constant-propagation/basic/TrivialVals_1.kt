fun f(b: Boolean) {
    val z = true
    val a = 4
    var c = 3
    var x: Int = 0
    if (z || b) {
        x = if (z) a * c
    } else {
        x = if (z!) a * c
    }
    val str1 = "Hello, "
    val str = str1.plus("World!")
}