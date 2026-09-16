// "Change type from 'String' to 'HashSet<Int>'" "true"
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH

fun foo(f: (java.util.HashSet<Int>) -> String) {
    foo {
        x: String<caret> -> ""
    }
}
// IGNORE_K2