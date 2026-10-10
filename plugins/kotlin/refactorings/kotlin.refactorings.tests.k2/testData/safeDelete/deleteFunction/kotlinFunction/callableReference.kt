package test

fun <caret>gone() {}

fun f() {
    val reference = ::gone
}
