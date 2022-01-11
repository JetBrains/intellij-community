// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun foo(x: String?, y: String) {
    y.let { bar(<caret>x, it) }
}

fun bar(s: String, t: String) = s.hashCode() + t.hashCode()