package p

typealias <caret>I = Int

fun sumI(x: I, vararg n: p.I): I = x + n.sum()

fun main() {
    println(sumI(1, *intArrayOf(2, 3)) + sumI(4, 5))
}
// Caused by KT-92063.
// IGNORE_K2
