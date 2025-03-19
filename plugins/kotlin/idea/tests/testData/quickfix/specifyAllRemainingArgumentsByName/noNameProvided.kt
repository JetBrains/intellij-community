// "Specify all remaining arguments by name" "false"
// WITH_STDLIB
fun main() {
    val dc = DataClass(""<caret>)
}

private data class DataClass(val : String)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyAllRemainingArgumentsByNameFix