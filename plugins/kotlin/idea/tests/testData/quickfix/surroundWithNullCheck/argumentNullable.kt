// "Surround with null check" "true"

fun foo(x: String?) {
    bar(<caret>x)
}

fun bar(s: String) = s.hashCode()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix