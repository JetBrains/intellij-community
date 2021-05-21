// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(array: Array<String>?) {
    var s = ""
    s = array[0]<caret>
}

/* IGNORE_FIR */
