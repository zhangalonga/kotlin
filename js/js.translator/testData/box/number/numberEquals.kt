// EXPECTED_REACHABLE_NODES: 1112
package foo

fun box(): String {
    var x: Double = +0.0
    var y: Double = -0.0

    if (x.equals(y)) return "Total order fail"
    if (x != y) return "IEEE 754 equals fail"

    return "OK"
}