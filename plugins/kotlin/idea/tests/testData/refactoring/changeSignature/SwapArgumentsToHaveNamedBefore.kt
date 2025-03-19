data class <caret>Foo(val p1: Int, val p2: Int, val p3: Int = 2){}

fun bar() {
    Foo(0, 1)
}