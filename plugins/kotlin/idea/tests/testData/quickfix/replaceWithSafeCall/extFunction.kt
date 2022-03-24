// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
fun String?.foo() {
    <caret>toLowerCase()
}
/* IGNORE_FIR */