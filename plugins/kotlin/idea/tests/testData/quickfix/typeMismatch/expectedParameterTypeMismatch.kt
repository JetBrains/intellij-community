// "Change type from 'String' to 'Int'" "true"
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH
fun foo(f: (Int) -> String) {
    foo {
        x: String<caret> -> ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2