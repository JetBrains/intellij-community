package foo

class <caret>A {
    fun m() {}
    fun foo(a: Any) {
        if (a is A) {
            a.m()
        }
    }
}

fun dummy() {}