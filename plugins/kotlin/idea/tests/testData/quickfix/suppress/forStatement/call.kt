// "Suppress 'DIVISION_BY_ZERO' for statement " "true"

fun foo() {
    call(2 / <caret>0)
}

fun call(i: Int) {}
