package a

class A {
    inner class Inner {
        inner class Inner2 {
            fun innerFun() {
                b()
            }
        }
    }

    fun b() {}
}
