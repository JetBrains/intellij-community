// IGNORE_K2
// See KTIJ-31892

// ERROR: Cannot perform refactoring.\nVariable length has no initializer

fun foo(s: String) {
    val l = s.<caret>length
}