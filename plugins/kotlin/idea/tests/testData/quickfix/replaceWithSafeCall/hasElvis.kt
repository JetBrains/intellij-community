// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun main() {
    var a = foo()<caret>.length ?: 0
}

fun foo(): String? {
    return ""
}
