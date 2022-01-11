// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

val it = ""

fun test(s: String?) {
    val name = ""
    bar(<caret>s)
}

fun bar(name: String) {}
