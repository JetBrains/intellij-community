// "Replace with safe (?.) call" "true"
// WITH_STDLIB
class T {
    fun foo(s: String?) = s<caret>.length
}
