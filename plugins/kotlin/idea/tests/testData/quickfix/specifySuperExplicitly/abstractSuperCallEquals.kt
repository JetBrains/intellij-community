// "Generate 'equals()'" "true"
// WITH_STDLIB
// ERROR: Abstract member cannot be accessed directly

interface I

abstract class A {
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
}

class B : A(), I {
    override fun equals(other: Any?): Boolean {
        return super.<caret>equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}




// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AbstractSuperCallFix