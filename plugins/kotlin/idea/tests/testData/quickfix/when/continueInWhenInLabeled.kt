// "Add '@loop' to continue" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.3

fun foo(chars: CharArray) {
    val length = chars.size
    var pos = 0
    loop@ while (pos < length) {
        val c = chars[pos]
        when (c) {
            '\n' -> continue<caret>
        }
        pos++
    }
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddLoopLabelFix