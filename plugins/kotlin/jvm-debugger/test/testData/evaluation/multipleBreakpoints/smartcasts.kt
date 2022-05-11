package smartcasts

fun main(args: Array<String>) {
    test1(Derived())
    test1(Base())

    test2(Derived())
    test2(null)
}

// EXPRESSION: derived.prop
// RESULT: 1: I

// EXPRESSION: derived.prop
// RESULT: java.lang.ClassCastException : smartcasts.Base cannot be cast to smartcasts.Derived
fun test1(derived: Base) =
        derived is Derived &&
        //Breakpoint!
        derived.prop == 1

// EXPRESSION: nullable.prop
// RESULT: 1: I

// EXPRESSION: nullable.prop
// RESULT: Method threw 'java.lang.NullPointerException' exception.
fun test2(nullable: Derived?) =
        nullable != null &&
        //Breakpoint!
        nullable.prop == 1

class Derived : Base() {
    val prop = 1
}

open class Base

// TODO:
// Muted on IR evaluator as the IR backend exhibits different stepping behavior around
// boolean operators and expression bodies; the breakpoint in the failure cases are
// not hit. See the singleBreakpoint/simpleSmartcasts suite for examples.