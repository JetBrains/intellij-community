// "Change parameter 'a' type of function 'times' to 'String'" "true"
interface A {
    operator fun times(a: A): A
}

fun foo(a: A): A = a * <caret>""
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix