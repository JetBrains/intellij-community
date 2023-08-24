// "Surround with null check" "true"
// WITH_STDLIB

fun Int.bar() = this

fun foo(arg: Int?) {
    run(fun() = arg<caret>.bar())
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix