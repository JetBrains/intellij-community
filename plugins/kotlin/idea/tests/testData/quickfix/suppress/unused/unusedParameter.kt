// "Suppress 'UNUSED_PARAMETER' for fun foo" "true"
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
class X {
    fun foo(<caret>value: Int) {}
}
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix