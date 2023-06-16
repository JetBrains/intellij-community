// "Remove suppression" "true"

@Suppress("unused", "MoveVariableDec<caret>larationIntoWhen")
fun function() {

}

// IGNORE_FIR
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
