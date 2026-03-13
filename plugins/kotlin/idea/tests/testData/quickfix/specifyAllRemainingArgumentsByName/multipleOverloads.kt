// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'a'.
// K2_ERROR: No value passed for parameter 'b'.
// K2_ERROR: None of the following candidates is applicable:<br><br>fun foo(a: Int, b: Int): Unit<br>fun foo(a: String, b: String, c: String): Unit:<br>  No value passed for parameter 'c'.
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}

fun test() {
    foo(<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix