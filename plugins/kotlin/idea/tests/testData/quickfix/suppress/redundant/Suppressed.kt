// "Remove suppression" "false"
// ACTION: Convert to raw string literal
// ACTION: Do not show hints for current method
// ACTION: Specify type explicitly
// ACTION: Split property declaration

fun a(i: Int) {
    @Suppress("<caret>MoveVariableDeclarationIntoWhen") val suppressed = 4
    when (suppressed) {
        i -> Unit
        else -> Unit
    }
}

// IGNORE_K2
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.MoveVariableDeclarationIntoWhenInspection
