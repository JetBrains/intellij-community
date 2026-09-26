package sample

class Target

class Src {
    companion object {
        fun <caret>foo(t: Target) {
            println(t)
        }
    }
}

fun caller(target: Target) {
    Src.foo(target)
}
