fun String.<caret>foo(): Int = length

fun test() {
    val f: (String) -> Int = String::foo
    f("a")
}