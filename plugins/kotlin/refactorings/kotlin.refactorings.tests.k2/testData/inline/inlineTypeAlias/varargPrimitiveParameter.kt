typealias <caret>I = Int

fun sumI(vararg n: I): Int = n.sum()

fun main() {
    println(sumI(*intArrayOf(1, 2)) + sumI(3))
}
// Caused by KT-92063.
// IGNORE_K2
