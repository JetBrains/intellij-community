// "Change return type of enclosing function 'A.foo' to 'Int'" "true"
package foo.bar

class A {
    fun foo(): String {
        return <caret>1
    }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing