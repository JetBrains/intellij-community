open class A {

}

class <caret>B : A() {
    // INFO: {"checked": "true", "toAbstract": "true"}
    fun foo(n: Int = 1) {

    }
}

fun test() {
    B().foo()
    B().foo(2)
}