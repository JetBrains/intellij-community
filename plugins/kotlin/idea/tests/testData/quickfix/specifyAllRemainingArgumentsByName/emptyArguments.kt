// "Specify all remaining arguments by name" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 2.4
// K2_ERROR: None of the following candidates is applicable:<br><br>fun <T, R> context(with: T, block: context(T) () -> R): R:<br>  No value passed for parameter 'with'.<br><br>fun <A, B, R> context(a: A, b: B, block: context(A, B) () -> R): R:<br>  No value passed for parameter 'a'.<br>  No value passed for parameter 'b'.<br><br>fun <A, B, C, R> context(a: A, b: B, c: C, block: context(A, B, C) () -> R): R:<br>  No value passed for parameter 'a'.<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br><br>fun <A, B, C, D, R> context(a: A, b: B, c: C, d: D, block: context(A, B, C, D) () -> R): R:<br>  No value passed for parameter 'a'.<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br>  No value passed for parameter 'd'.<br><br>fun <A, B, C, D, E, R> context(a: A, b: B, c: C, d: D, e: E, block: context(A, B, C, D, E) () -> R): R:<br>  No value passed for parameter 'a'.<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br>  No value passed for parameter 'd'.<br>  No value passed for parameter 'e'.<br><br>fun <A, B, C, D, E, F, R> context(a: A, b: B, c: C, d: D, e: E, f: F, block: context(A, B, C, D, E, F) () -> R): R:<br>  No value passed for parameter 'a'.<br>  No value passed for parameter 'b'.<br>  No value passed for parameter 'c'.<br>  No value passed for parameter 'd'.<br>  No value passed for parameter 'e'.<br>  No value passed for parameter 'f'.
context(l: String)
fun ctxFun() {}

context(_: String)
fun myFun() {
    <caret>context() {
        ctxFun()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix