fun whenFun(b: Boolean) {
    var x: Int
    when (b) {
        true -> x = 3
        false -> x = 4
    }
    val z: Int
    when (b) {
        true -> z = 5
        false -> z = 5
    }
}