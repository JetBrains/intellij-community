// "Change type of 'f' to '(Delegates) -> Unit'" "true"
// WITH_STDLIB
// K2_ERROR: Initializer type mismatch: expected 'Int', actual '(Delegates) -> Unit'.

fun foo() {
    var f: Int = { x: kotlin.properties.Delegates ->  }<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix