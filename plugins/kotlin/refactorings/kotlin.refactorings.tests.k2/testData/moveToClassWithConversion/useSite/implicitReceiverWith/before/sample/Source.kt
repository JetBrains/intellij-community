package sample

class Target

fun <caret>foo(t: Target) {
    println(t)
}

fun caller(target: Target) {
    with(target) {
        foo(this)
    }
}
