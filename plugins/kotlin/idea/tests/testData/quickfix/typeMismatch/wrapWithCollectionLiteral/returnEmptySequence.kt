// "Replace with 'emptySequence()' call" "true"
// WITH_STDLIB

fun foo(a: String?): Sequence<String> {
    val w = a ?: return null<caret>
    return sequenceOf(w)
}
