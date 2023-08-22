// "Reorder parameters" "true"
fun foo(
    a: String,
    b: String = d<caret>,
    c: String,
    d: String,
    f: String,
) {}

fun main() {
    foo("a", "b", "c", "d", "e")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix