// "Specify 'String' type for enclosing property 'a'" "true"

val a
    get() = r<caret>eturn ""

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix