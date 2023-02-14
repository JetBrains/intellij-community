// "Remove suppression" "true"

@Suppress("unused", "MoveVariableDec<caret>larationIntoWhen")
fun function() {

}

// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
