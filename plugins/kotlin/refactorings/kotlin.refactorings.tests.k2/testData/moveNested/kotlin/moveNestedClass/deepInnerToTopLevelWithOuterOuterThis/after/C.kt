package test

class C(private val b: A.B) {
    fun test() {
        A.OuterOuterY()
        this@A.OuterOuterY()
    }
}