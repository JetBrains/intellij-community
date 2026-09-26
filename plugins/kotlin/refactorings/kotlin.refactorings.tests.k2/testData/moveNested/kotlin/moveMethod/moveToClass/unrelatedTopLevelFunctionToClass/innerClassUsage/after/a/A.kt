package a

class A {
    inner class Inner {
        fun innerFun() {
            b()
        }
    }

    fun b() {}
}
