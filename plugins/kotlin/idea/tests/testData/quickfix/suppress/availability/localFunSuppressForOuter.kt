// "Suppress 'DIVISION_BY_ZERO' for fun foo" "true"

fun foo() {
    fun local() = 2 / <caret>0
}
