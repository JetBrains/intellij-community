// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply {
        <caret>toLowerCase()
    }
}
/* IGNORE_FIR */