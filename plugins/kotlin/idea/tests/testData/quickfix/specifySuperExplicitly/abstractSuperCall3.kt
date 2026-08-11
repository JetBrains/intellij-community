// "Specify super type 'I' explicitly" "false"
// ACTION: Convert to block body
// ACTION: Go To Super Method
// ACTION: Introduce local variable
// ACTION: Specify supertype
// ERROR: Many supertypes available, please specify the one you mean in angle brackets, e.g. 'super<Foo>'
// K2_AFTER_ERROR: AMBIGUOUS_SUPER
// K2_ERROR: AMBIGUOUS_SUPER

interface I {
    fun foo(): String
}

abstract class A {
    abstract fun foo(): String
}

class B : A(), I {
    override fun foo(): String = super<caret>.foo()
}
