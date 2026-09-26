// INLINE_PROPERTY_KEEP: true

fun simpleFun(): String {
    val s<caret> = "string"
    val s2 = s
    return s
}