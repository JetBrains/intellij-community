package a

fun b<caret>() {}

class A {
    inner class Inner {
        fun innerFun() {
            b()
        }
    }
}
