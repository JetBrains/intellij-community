// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo() {}

fun bar() {
    val fff: (() -> Unit)? = ::foo
    <caret>fff()
}

/* IGNORE_FIR */
