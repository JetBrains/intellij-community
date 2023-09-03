// "Remove suppression" "true"

@Suppress("<caret>MoveVariableDeclarationIntoWhen")
fun function() {
    
}

// IGNORE_K2
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.RemoveRedundantSuppression