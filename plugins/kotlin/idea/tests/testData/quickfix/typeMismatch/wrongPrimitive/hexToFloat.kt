// "Change to '15F'" "true"
// K2_ERROR: Initializer type mismatch: expected 'Float', actual 'Int'.

val a : Float = 0xF<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrongPrimitiveLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrongPrimitiveLiteralFix