// "Change type from 'String' to '(Int) -> String'" "true"
fun foo(f: ((Int) -> String) -> String) {
    foo {
        f: String<caret> -> f(42)
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2