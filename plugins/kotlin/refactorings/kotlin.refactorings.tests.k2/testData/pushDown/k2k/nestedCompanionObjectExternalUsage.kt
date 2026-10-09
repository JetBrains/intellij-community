open class <caret>A {
    // INFO: {"checked": "true"}
    class B {
        class C {
            companion object {
                fun m() {}
            }
        }
    }
}

class D: A()

fun n() {
    A.B.C.m()
}