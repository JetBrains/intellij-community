package test1

class Inner(private val test: Test) {
    fun foo() {
        test.bar()
    }
}