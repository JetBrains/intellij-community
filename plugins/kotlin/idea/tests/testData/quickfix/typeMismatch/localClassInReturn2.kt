// "Change return type of enclosing function 'foo' to 'T'" "true"
interface T

fun foo() {
    open class A: T
    class B: A()

    return <caret>B()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
/* IGNORE_K2 */