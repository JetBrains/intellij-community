// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?) {
    val s2 = notNull(notNull(<caret>s))
}

fun notNull(name: String): String = name