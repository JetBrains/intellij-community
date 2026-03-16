// "Specify 'Unit' return type for enclosing function 'test'" "true"

fun test() = run { if (true) re<caret>turn }

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix