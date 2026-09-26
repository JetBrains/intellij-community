package sample

class Target

class Src {
    fun <caret>foo(t: Target) {
        println(t)
    }
}

fun caller(src: Src, target: Target) {
    src.foo(target)
}
