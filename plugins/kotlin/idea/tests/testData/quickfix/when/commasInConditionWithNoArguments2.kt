// "Replace ',' with '||' in when" "true"
fun test(a: Boolean, b: Boolean, c: Boolean) {
    val c = when {
        a<caret>, b -> "a"
        c -> "b"
        else -> "e"
    }
}
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CommaInWhenConditionWithoutArgumentFix