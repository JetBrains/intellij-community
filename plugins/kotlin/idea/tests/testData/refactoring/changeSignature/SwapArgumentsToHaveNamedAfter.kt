data class <caret>Foo(val p1: Int, val p3: Int = 2, val p2: Int){}

fun bar() {
    Foo(0, p2 = 1)
}