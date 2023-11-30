// "Specify supertype" "true"
interface X

open class Y {
    open fun foo() {}
}

interface Z {
    fun foo() {}
}

class Test : X, Y(), Z {
    override fun foo() {}

    inner class Boo : Y(), Z {
        override fun foo() {
            <caret>super@Test.foo()
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifySuperTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SpecifySuperTypeFixFactory$applicator$1