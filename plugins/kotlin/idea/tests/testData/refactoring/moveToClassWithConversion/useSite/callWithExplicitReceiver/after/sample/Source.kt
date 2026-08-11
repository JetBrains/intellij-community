package sample

class Target {
    fun foo() {
        println(this)
    }
}

class Src {
}

fun caller(src: Src, target: Target) {
    target.foo()
}

