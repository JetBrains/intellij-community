// "Replace with safe (?.) call" "true"
class A {
    operator fun plus(other: A) = this
}

fun foo(b: A) {
    var a: A? = A()
    a <caret>+= b
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix