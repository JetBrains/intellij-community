package a

class A {
    fun b() {}
}

fun hasWith(i: A) {
    with(i) {
        b()
    }
}
