// WITH_DEFAULT_VALUE: false
class A {
    inner class B {
        fun m() {
            <selection>this@A</selection>
        }
    }

    fun mmm(b: B) {
        b.m()
    }
}
fun mm(a: A) {
    a.B().m() //bug
}