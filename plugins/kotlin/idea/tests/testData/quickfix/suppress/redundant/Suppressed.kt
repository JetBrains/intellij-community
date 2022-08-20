// "Remove suppression" "false"
// ACTION: Do not show hints for current method
// ACTION: Specify type explicitly
// ACTION: Split property declaration
// ACTION: To raw string literal

fun a(i: Int) {
    @Suppress("<caret>MoveVariableDeclarationIntoWhen") val suppressed = 4
    when (suppressed) {
        i -> Unit
        else -> Unit
    }
}

// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
