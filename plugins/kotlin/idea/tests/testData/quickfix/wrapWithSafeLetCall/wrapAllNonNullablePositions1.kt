// "Wrap with '?.let { ... }' call" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ERROR: Type mismatch: inferred type is String? but String was expected
// WITH_STDLIB

fun test(s: String?) {
    nullable(nullable(notNull(notNull(<caret>s))))
}

fun notNull(name: String): String = name
fun nullable(name: String?): String = ""