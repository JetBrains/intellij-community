// "Change return type of called function 'A.not' to 'A'" "true"
interface A {
    operator fun not(): String
    operator fun times(a: A): A
}

fun foo(a: A): A = a * <caret>!(if (true) a else a)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled