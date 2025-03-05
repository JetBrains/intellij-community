package bar

class Test {
    val a: Int = 5
}

fun Test.outside(): Int {
    return foo(this, 5)
}