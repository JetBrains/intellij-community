package sample

class Target {
    fun foo() {
        println(this)
    }
}

class Other

fun caller(target: Target, other: Other) {
    with(other) {
        with(target) {
            target.foo()
        }
    }
}
