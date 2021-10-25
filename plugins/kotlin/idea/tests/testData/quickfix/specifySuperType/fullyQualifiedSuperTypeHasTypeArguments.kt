// "Specify supertype" "true"
// SHOULD_DIFFER_FROM_FE10
package a.b.c

interface X {}

open class Y<T> {
    open fun foo() {}
}

interface Z {
    fun foo() {}
}

class Test : a.b.c.Y<Int>(), X, Z {
    override fun foo() {
        <caret>super.foo()
    }
}