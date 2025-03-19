package a

class Foo(val x: Int, val y: Int) {
    data class Bar(val a: Int, val b: Int)

    val bar = Bar(0, 0)
}