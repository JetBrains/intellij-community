fun foo(a: Int, <caret>b: Int = 0, c: Int = 4) {

}

fun bar() {
    foo(1, c = 3)
}