// "Suppress 'UnusedImport' for file ${file}" "true"

import<caret> java.io.*

// K1_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.KotlinUnusedImportInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.KotlinUnusedImportInspection
// FUS_QUICKFIX_NAME: com.intellij.codeInspection.SuppressIntentionActionFromFix