// "Change parameter 'x' type of primary constructor of class 'Foo' to 'T & Any'" "true"
// LANGUAGE_VERSION: 1.8
// K2_ERROR: Argument type mismatch: actual type is 'T (of class Foo<T>)', but 'T (of class Foo<T>) & Any' was expected.

class Foo<T>(x: T) {
    init {
        foo(<caret>x)
    }

    fun foo(y: T & Any) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix