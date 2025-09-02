// "Generate 'hashCode()'" "true"
// WITH_STDLIB
// ERROR: Abstract member cannot be accessed directly
// K2_AFTER_ERROR: Abstract member cannot be accessed directly.

interface I

abstract class A {
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
}

class B : A(), I {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.<caret>hashCode()
    }
}




// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AbstractSuperCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.UpdateToCorrectMethodFix