fun foo(a: Int, b: Int = 0, <caret>c: Int = 0) {

}

fun bar() {
    foo(1)
}