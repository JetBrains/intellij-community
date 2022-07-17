// "Remove condition" "true"
fun foo(): Int = 1

fun println(i: Int) {}

fun main() {
    when (foo()) {
        <caret>null, 1 -> println(1)
        else -> println(0)
    }
}

/* IGNORE_FIR */
