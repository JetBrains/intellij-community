// "Add 'toString()' call" "false"
// ERROR: This function must return a value of type String
// K2_ERROR: Return type mismatch: expected 'String', actual 'Unit'.
// K2_AFTER_ERROR: Return type mismatch: expected 'String', actual 'Unit'.

fun test(num: Int): String {
    if (num == 0) {
        return<caret>
    }
    return "Hello, World!"
}
