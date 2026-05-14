package a

class A {
    companion object {
        fun A.companionFun() {
            b()
        }
    }

    fun b() {}
}
