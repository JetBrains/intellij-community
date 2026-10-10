package test

fun <caret>first() {
    second()
}

fun <caret>second() {
    first()
}

fun kept() {}
