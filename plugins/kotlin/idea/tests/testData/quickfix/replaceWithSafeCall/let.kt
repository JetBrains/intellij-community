// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.let {
        it<caret>.length
    }
}
/* FIR_COMPARISON */