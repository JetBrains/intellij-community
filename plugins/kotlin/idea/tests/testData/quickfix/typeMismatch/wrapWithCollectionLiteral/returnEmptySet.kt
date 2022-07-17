// "Replace with 'emptySet()' call" "true"
// WITH_STDLIB

fun foo(a: String?): Set<String> {
    val w = a ?: return null<caret>
    return setOf(w)
}
