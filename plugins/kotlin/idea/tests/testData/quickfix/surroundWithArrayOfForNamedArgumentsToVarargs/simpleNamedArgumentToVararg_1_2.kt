// "Surround with *arrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// IGNORE_K2

