// "Change type of 'list' to 'List<Any>'" "false"
// WITH_STDLIB
// K2_AFTER_ERROR: Initializer type mismatch: expected 'List<Any>', actual 'List<Comparable<*>? & Serializable?>'.



fun mixed() {
    val list: List<Any> = lis<caret>tOf(1, "string", null, 3.14)
}

// IGNORE_K1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix