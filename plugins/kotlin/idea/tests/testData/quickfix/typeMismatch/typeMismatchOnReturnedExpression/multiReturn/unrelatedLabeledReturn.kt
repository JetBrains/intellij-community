// "Change return type of enclosing function 'bar' to 'String?'" "true"
// WITH_STDLIB
fun bar(n: Int): Boolean {
    if (true) return "bar"<caret>
    val list = listOf(1).map {
        return@map it + 1
    }
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix