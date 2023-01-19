// "Specify return type explicitly" "true"
interface I
fun <caret>foo() = if (false) object : I {} else null
