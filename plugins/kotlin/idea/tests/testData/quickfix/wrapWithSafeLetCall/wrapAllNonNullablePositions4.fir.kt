// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?): String? {
    if (true) {
        notNull(notNull(<caret>s))
    }
}

fun notNull(name: String): String = name