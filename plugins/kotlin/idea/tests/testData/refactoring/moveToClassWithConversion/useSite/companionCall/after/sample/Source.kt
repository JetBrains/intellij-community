package sample

class Target {
    fun foo() {
        println(this)
    }
}

class Src {
}

fun caller(target: Target) {
    target.foo()
}

