// "Change parameter 'f' type of function 'foo' to '() -> String'" "true"
fun foo(f: () -> Int) {
    foo {
        ""<caret>
    }
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing