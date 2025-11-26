// "Specify 'String' return type for enclosing function 'a'" "true"
// K2_AFTER_ERROR: Returns are prohibited in functions with expression body. Use block body '{...}'.

fun a() = r<caret>eturn ""

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix