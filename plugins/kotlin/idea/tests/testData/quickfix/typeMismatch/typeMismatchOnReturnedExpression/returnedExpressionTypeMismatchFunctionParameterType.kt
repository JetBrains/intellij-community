// "Change parameter 'f' type of function 'foo' to '() -> String'" "true"
fun foo(f: () -> Int) {
    foo {
        ""<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// IGNORE_K2
// For K2, see KTIJ-33125