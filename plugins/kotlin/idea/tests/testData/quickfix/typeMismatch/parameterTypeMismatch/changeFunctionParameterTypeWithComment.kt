// "Change parameter 'f' type of function 'foo' to '() -> Int'" "true"
fun foo(f: () -> String) {}

fun test() {
    foo {
        <caret>1 // comment
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// IGNORE_K2
// For K2, see KTIJ-33125