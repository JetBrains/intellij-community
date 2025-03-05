// "Change parameter 'x' type of primary constructor of class 'Foo' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
class Foo<T>(val x: T) {
    fun foo(y: T & Any) {}
}

fun <T> Foo<T>.bar(x: T) {
    foo(<caret>this.x)
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix