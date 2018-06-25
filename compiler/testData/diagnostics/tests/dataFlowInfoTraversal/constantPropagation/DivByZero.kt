// !LANGUAGE: +ConstantPropagation
fun divByZero(i: Int) {
    val z = 10
    val b = 20
    val m = 33
    val a = 0

    if (<!DIVISION_BY_ZERO!>(z + b + m) / (a * i)<!> > 10) {
    }
}

1 // !LANGUAGE: +ConstantPropagation
2 fun divByZero(i: Int) {
    3   val z = 10
    4   val b = 20
    5   val m = 33
    6   val a = 0
    7   if (<!DIVISION_BY_ZERO!>(z + b + m) / (a * i)<!> > 10) {
        8   }
    9 }