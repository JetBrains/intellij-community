// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun foo(x: String?) {
    bar(<caret>x)
}

fun bar(s: String) = s.hashCode()