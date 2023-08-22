fun main() {
    val bar = 5
    boo(foo(bar)<caret>)
}

fun foo(a: Int): Int = 5
fun boo(a: Int): Int = 5

/*
foo(...)
boo(...)
*/