// "Generate 'hashCode()'" "true"
// WITH_STDLIB

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



