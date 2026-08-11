// "Change type from 'String' to '(Int) -> String'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH
fun foo(f: ((Int) -> String) -> String) {
    foo {
        f: String<caret> -> f(42)
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2