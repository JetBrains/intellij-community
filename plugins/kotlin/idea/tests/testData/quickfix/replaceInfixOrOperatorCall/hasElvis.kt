// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo(list: List<String>?) {
    var s = ""
    s = list[0]<caret> ?: ""
}

/* IGNORE_FIR */
