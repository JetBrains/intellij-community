// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun Int.foo(x: Int) = this + x

val arg: Int? = 42

val res = 24.hashCode().foo(<caret>arg) + 1
