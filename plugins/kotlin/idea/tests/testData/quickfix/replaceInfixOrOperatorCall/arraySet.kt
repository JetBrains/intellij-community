// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(array: Array<String>?) {
    array<caret>[0] = ""
}

/* IGNORE_FIR */
