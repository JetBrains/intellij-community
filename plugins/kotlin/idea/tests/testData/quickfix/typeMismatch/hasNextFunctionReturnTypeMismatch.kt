// "Change return type of called function 'A.hasNext' to 'Boolean'" "true"
// K2_ERROR: HAS_NEXT_FUNCTION_TYPE_MISMATCH
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
abstract class A {
    abstract operator fun hasNext(): Int
    abstract operator fun next(): Int
    abstract operator fun iterator(): A
}

fun test(notRange: A) {
    for (i in notRange<caret>) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled
// IGNORE_K2
// For K2, needs KT-75197