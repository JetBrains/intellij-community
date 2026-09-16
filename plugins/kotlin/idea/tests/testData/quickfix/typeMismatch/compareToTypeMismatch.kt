// "Change return type of called function 'A.compareTo' to 'Int'" "true"
// K2_ERROR: COMPARE_TO_TYPE_MISMATCH
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
interface A {
    operator fun compareTo(other: Any): String
}
fun foo(x: A) {
    if (x <<caret> 0) {}
}
// IGNORE_K2
// For K2, needs KT-75197