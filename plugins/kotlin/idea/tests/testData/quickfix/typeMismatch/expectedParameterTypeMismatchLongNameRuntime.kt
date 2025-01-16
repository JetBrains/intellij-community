// "Change type from 'String' to 'HashSet<Int>'" "true"

fun foo(f: (java.util.HashSet<Int>) -> String) {
    foo {
        x: String<caret> -> ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeTypeFix
// IGNORE_K2