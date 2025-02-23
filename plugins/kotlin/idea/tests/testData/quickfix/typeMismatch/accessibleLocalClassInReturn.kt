// "Change return type of enclosing function 'bar' to 'A'" "true"
fun foo() {
    open class A

    fun bar(): Int {
        return <caret>object: A() {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// IGNORE_K2
// For K2, see KTIJ-33125