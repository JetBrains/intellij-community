package test

class C(private val a: A.B) {
    fun test() {
        A.OuterOuterY()
        this@A.OuterOuterY()
    }
}