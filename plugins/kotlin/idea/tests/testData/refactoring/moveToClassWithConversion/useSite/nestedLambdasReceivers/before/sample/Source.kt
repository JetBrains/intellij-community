package sample

class Target

class Other

fun <caret>foo(t: Target) {
    println(t)
}

fun caller(target: Target, other: Other) {
    with(other) {
        with(target) {
            foo(target)
        }
    }
}
