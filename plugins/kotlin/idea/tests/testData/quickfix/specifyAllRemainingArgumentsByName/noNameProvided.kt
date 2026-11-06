// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
// K2_ERROR: SYNTAX
// K2_AFTER_ERROR: SYNTAX
fun main() {
    val dc = DataClass(""<caret>)
}

private data class DataClass(val : String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix