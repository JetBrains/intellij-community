// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface Str {
    val foo: (() -> Unit)?
}

object Str2 {
    val foo2: (Str.() -> Unit)? = null

    fun bar(s: Str) {
        s.<caret>foo2()
    }
}

