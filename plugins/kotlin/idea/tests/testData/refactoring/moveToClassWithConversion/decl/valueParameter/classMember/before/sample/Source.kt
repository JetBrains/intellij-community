package sample

class Target

class Src {
    fun <caret>foo(t: Target) {
        println(t)
    }
}
