package a

fun b<caret>() {}

class A {
    inner class Inner {
        inner class Inner2 {
            fun innerFun() {
                b()
            }
        }
    }
}
