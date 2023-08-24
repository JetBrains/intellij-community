// "Surround with *arrayOf(...)" "true"

class Foo<T>(vararg val p: T)

fun test() {
    Foo(p = 123<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix