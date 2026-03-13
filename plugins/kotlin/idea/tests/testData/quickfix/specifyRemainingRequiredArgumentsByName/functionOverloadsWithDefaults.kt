// "Specify remaining required arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'a'.
// K2_ERROR: None of the following candidates is applicable:<br><br>fun foo(a: Int, b: String): Unit:<br>  No value passed for parameter 'b'.<br><br>fun foo(a: Int, b: String, c: Int): Unit:<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br><br>fun foo(a: Int, b: String = ..., c: Int = ..., d: Int = ...): Unit
fun foo(a: Int, b: String) {}
fun foo(a: Int, b: String, c: Int) {}
fun foo(a: Int, b: String = "", c: Int = 5, d: Int = 5) {}

fun test() {
    foo<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyRemainingRequiredArgumentsByNameFix