package dependency

class A

class B {
    val a = A()
    fun foo(lambda: (A) -> Boolean) {}
}