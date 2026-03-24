// "Specify supertype" "true"
// K2_ERROR: Multiple supertypes available. Specify the intended supertype in angle brackets, e.g. 'super<Foo>'.
interface X

open class Y {
    open val bar
        get() = 1
}

interface Z {
    val bar
        get() = 2
}

class Test : X, Z, Y() {
    override val bar: Int
        get() = <caret>super.bar
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SpecifySuperTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifySuperTypeFixFactory$SpecifySuperTypeQuickFix