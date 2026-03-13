// "Specify super type 'IntA' explicitly" "true"
// K2_ERROR: Abstract member cannot be accessed directly.

interface IntA {
    fun check(): String = "OK"
}

interface IntB {
    fun check(): String
}

abstract class AbstractClassA {
    abstract fun check(): String
}

abstract class DerivedA : AbstractClassA(), IntA


class ContainsNested {
    class DerivedB : DerivedA(), IntB {

        override fun check(): String {
            // Dispatched to AbstractClassA.check()
            return super.<caret>check()
        }
    }

}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AbstractSuperCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SpecifySuperTypeExplicitlyFix