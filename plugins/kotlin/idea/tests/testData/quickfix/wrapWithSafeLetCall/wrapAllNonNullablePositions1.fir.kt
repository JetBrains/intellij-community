// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?) {
    nullable(nullable(notNull(notNull(<caret>s))))
}

fun notNull(name: String): String = name
fun nullable(name: String?): String = ""