// "Change parameter 'y' type of function 'foo' to 'T'" "true"
// LANGUAGE_VERSION: 1.8
// K2_ERROR: Argument type mismatch: actual type is 'T (of fun <T> Foo<T>.bar)', but 'T (of fun <T> Foo<T>.bar) & Any' was expected.
class Foo<T>(val x: T) {
    fun foo(y: T & Any) {}
}

fun <T> Foo<T>.bar(x: T) {
    foo(<caret>this.x)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix