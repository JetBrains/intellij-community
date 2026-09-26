// "Add safe '?.toString()' call" "false"
// ERROR: This function must return a value of type String?
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH

fun test(num: Int): String? {
    if (num == 0) {
        return<caret>
    }
    return "Hello, World!"
}
