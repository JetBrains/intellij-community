// "Change type from 'String' to '(LinkedHashSet<Int>) -> HashSet<Int>'" "true"
// K2_ERROR: EXPECTED_PARAMETER_TYPE_MISMATCH

fun foo(f: ((java.util.LinkedHashSet<Int>) -> java.util.HashSet<Int>) -> String) {
    foo {
        f: String<caret> -> "42"
    }
}
// IGNORE_K2