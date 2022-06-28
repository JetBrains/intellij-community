// "Suppress unused warning if annotated by 'kotlin.Deprecated'" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
@Deprecated("")
fun foo<caret>(){}
