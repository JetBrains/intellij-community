// "Specify return type explicitly" "true"
interface I
fun <caret>foo() = if (false) object : I {} else null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix