// "Change return type of enclosing function 'Companion.foo' to 'Int'" "true"
package foo.bar

class A {
    companion object {
        fun foo(): String {
            return <caret>1
        }
    }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix