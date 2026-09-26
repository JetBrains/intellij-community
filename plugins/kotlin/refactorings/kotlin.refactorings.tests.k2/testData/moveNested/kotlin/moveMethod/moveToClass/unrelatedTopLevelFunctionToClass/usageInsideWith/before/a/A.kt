package a

fun b<caret>() {}

class A {
}

fun hasWith(i: A) {
    with(i) {
        b()
    }
}
