// "Change type from 'String' to 'Int'" "true"
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH
fun foo(f: (Int) -> String) {
    foo {
        x: String<caret> -> ""
    }
}
// IGNORE_K2