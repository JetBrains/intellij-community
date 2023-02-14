// "Suppress 'UNNECESSARY_NOT_NULL_ASSERTION' for statement " "true"
// WITH_STDLIB

fun foo(): Any {
    throw Exception(""<caret>!!)
}