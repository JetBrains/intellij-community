// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun bar() {
    val fff: (() -> Int)? = { 1 }
    var i: Int = 1
    i = fff<caret>()
}