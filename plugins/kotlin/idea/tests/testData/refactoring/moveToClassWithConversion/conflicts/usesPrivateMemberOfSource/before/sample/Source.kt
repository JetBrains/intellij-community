package sample

class Target

class Src {
    private val secret: Int = 42

    fun <caret>foo(t: Target) {
        println(secret)
        println(t)
    }
}
