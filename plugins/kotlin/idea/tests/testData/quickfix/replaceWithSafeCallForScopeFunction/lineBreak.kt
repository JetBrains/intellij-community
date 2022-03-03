// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    val b = a
            .let {
                it<caret>.length
            }
}