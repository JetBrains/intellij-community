package sample

class Target

interface Src {
    fun <caret>foo(t: Target) {
        println(t)
    }
}
