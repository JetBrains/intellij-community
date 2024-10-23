package test1

class Inner(private val a: Test) {
    fun foo() {
        a.bar()
    }
}