package sample

class Target {
    fun foo() {
        println(this)
    }
}

fun caller(target: Target) {
    with(target) {
        this.foo()
    }
}
