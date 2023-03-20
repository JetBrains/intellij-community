// INLINE_PROPERTY_KEEP: true

fun simpleFun(): String {
    val s = "string"
    val o = s
    return s<caret>
}