fun foo(x: Boolean) {
    val a = true
    val b = false
    var z = 1
    if (a && !b && (b || a)) {
        z = 0
    }
}