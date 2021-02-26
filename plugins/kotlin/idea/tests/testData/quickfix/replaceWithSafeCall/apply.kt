// "Replace with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply {
        this<caret>.length
    }
}
/* FIR_COMPARISON */