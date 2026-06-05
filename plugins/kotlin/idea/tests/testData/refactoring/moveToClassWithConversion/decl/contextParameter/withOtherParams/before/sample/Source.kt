package sample

class Target

context(t: Target)
fun <caret>foo(other: Int) {
    println(t)
    println(other)
}
