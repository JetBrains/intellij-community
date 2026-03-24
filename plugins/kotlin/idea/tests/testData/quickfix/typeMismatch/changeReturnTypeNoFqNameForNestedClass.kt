// "Change return type of enclosing function 'B.foo' to 'Int'" "true"
// K2_ERROR: Return type mismatch: expected 'String', actual 'Int'.
package foo.bar

class A {
    class B {
        fun foo(): String {
            return <caret>1
        }
    }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix