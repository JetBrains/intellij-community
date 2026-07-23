// "Specify super type 'when' explicitly" "true"
// K2_ERROR: ABSTRACT_SUPER_CALL

interface IntB {
    fun check(): String
}

abstract class AbstractClassA {
    abstract fun check(): String
}

abstract class DerivedA : AbstractClassA(), `when`

class DerivedB : DerivedA(), IntB, `when` {
    override fun check(): String = super<`when`>.check()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifySuperTypeExplicitlyFix
