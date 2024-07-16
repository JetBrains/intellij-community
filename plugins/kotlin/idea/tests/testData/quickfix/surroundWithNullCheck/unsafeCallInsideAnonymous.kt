// "Surround with null check" "true"
// WITH_STDLIB

fun Int.bar() = this

fun foo(arg: Int?) {
    run(fun() = arg<caret>.bar())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix