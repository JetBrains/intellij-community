// "Suppress 'unused' for class SomeUnusedEmptyClass" "true"

class SomeUnusedEmptyClass<caret>

// K1_TOOL: org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix