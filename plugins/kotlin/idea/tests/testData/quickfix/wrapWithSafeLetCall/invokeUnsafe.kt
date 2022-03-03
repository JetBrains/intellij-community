// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

operator fun Int.invoke() = this

fun foo(arg: Int?) {
    <caret>arg()
}