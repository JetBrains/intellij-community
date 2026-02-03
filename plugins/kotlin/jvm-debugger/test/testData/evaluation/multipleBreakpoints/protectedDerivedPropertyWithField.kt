package protectedDerivedPropertyWithField

fun main() {
    val derived = Derived()
    // EXPRESSION: derived.megaProperty
    // RESULT: "megaDerived": Ljava/lang/String;
    //Breakpoint!
    derived.foo()
}

abstract class Base {
    protected open val megaProperty: String? = null
    fun foo() {
        // EXPRESSION: megaProperty
        // RESULT: "megaDerived": Ljava/lang/String;
        //Breakpoint!
        "".toString()
    }
}

class Derived : Base() {
    override val megaProperty = "megaDerived"
}
