// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// K2_ERROR: No value passed for parameter 'b'.
// K2_ERROR: None of the following candidates is applicable:<br><br>fun foo(a: Int, b: Int): Unit:<br>  Argument type mismatch: actual type is 'String', but 'Int' was expected.<br><br>fun foo(a: String, b: String, c: String): Unit:<br>  No value passed for parameter 'c'.<br><br>fun foo(a: Int, b: Int, c: Int, d: Int): Unit:<br>  No value passed for parameter 'c'.<br>  No value passed for parameter 'd'.<br>  Argument type mismatch: actual type is 'String', but 'Int' was expected.
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: Int, b: Int, c: Int, d: Int) {}

fun test() {
    foo(a = ""<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix