// "Specify supertype" "true"
// DIFFERENT FROM FE1.0 AS INTENDED
package a.b.c

interface Z {
    fun foo() {}
}

open class X {
    open fun foo() {}
}

class Test : (@Suppress("foo") a.b.c.X)(), Z {
    override fun foo() {
        <caret>super.foo()
    }
}