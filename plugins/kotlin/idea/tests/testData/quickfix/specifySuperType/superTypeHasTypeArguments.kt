// "Specify supertype" "true"
interface X {}

open class Y<T> {
    open fun foo() {}
}

interface Z {
    fun foo() {}
}

class Test : Y<Int>(), X, Z {
    override fun foo() {
        <caret>super.foo()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifySuperTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SpecifySuperTypeFixFactory$applicator$1