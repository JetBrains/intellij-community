/**
 * Multi-line comment on data class
 */
data class Foo(
    // Single-line comment on p1
    val p1: String,
    // Single-line comment on p2
    val p2: String
) {
    override fun toString() = buildString {
        append("(")
        // code comment on p1 usage
        append(p1)
        append(",")
        // code comment on p2 usage
        append(p2)
        append(")")
    }
}
