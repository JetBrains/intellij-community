// "Suppress 'DIVISION_BY_ZERO' for statement " "true"

fun foo() {
    when ("") {
        is Any? -> {2 / <caret>0}
    }
}
