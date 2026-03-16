// "Specify 'String' return type for enclosing function 'a'" "true"

fun a() = r<caret>eturn ""

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix