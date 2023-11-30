// "Change return type of enclosing function 'bar' to 'String?'" "true"
// WITH_STDLIB
fun bar(n: Int): Boolean {
    if (true) return "bar"<caret>
    val list = listOf(1).map {
        return@map it + 1
    }
    return null
}

/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing