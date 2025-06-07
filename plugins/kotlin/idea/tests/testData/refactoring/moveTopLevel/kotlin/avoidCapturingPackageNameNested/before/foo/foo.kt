package foo

fun other(a: Int, b: Int): Int = a + b

fun bar<caret>(foo: Int) {
    other(other(other(1, 2), other(3, 4)), other(other(1, 2), other(3, 4)))
}
