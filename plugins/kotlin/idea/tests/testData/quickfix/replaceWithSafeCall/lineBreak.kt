// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    val b = a
            .<caret>length
}
/* FIR_COMPARISON */