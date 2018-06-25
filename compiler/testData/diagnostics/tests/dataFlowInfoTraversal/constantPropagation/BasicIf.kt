// !LANGUAGE: +ConstantPropagation
fun ifFun(k: Boolean) {
    val b = false
    val a = true
    val c = !b
    if (<!ALWAYS_TRUE_EXPRESSION!>a && c<!>) {
    }

    if (<!ALWAYS_TRUE_EXPRESSION!>a && !b<!>) {
    }

    if (<!ALWAYS_TRUE_EXPRESSION!>a && !b && (b || a)<!>) {
    }

    val x = 1
    val z = 3
    val m = 5
    if (<!ALWAYS_TRUE_EXPRESSION!>x + (z + m) == 9<!>) {
    }
    if (<!ALWAYS_TRUE_EXPRESSION!>m * z + x > 10<!>) {
    }

    if (<!ALWAYS_TRUE_EXPRESSION!>z / x + m <= 8<!>) {
    }
    if (<!ALWAYS_FALSE_EXPRESSION!>z * m - 10 - x > 8<!>){
    }
    if (k) {
        val k_b = k && b
        if (<!ALWAYS_FALSE_EXPRESSION!>k_b || (44 - z * m >= 100)<!>) {
        }
    } else {
        if (<!ALWAYS_FALSE_EXPRESSION!>k && true<!>) {
        }
    }
}

