// "Generate 'toString()'" "true"
// WITH_STDLIB

interface I

abstract class A {
    abstract override fun toString(): String
}

class B : A(), I {

    override fun toString(): String {
        return super.<caret>toString()
    }
}