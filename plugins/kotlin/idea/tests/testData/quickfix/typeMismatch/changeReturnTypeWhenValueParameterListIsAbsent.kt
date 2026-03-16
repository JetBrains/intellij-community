// "Specify 'Boolean' return type for called function 'A.hasNext'" "true"
abstract class A {
    abstract operator fun hasNext
    abstract operator fun next(): Int
    abstract operator fun iterator(): A
}

fun test(notRange: A) {
    for (i in notRange<caret>) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled
// IGNORE_K2
// For K2, needs KT-75197