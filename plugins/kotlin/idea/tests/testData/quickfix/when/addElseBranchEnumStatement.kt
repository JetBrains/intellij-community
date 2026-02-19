// "Add else branch" "true"
enum class Color { R, G, B }
fun use(c: Color) {
    <caret>when (c) {
        Color.R -> red()
    }
}

fun red() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddWhenElseBranchFix