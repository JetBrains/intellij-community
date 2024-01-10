// IGNORE_K2
class A {
    fun bar() {

    }

    fun foo() {
        <selection>bar()</selection>
        this.bar()
        this@A.bar()
    }
}