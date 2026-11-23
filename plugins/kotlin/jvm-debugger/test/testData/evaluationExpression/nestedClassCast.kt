package nestedClassCast

class Outer {
    class Nested<T>(val v: T)
}

class GenericOuter<T> {
    class Nested(val w: Int)
}

fun main() {
    val x: Any = Outer.Nested(42)
    val y: Any = GenericOuter.Nested(7)
    //Breakpoint!
    println()
}

// A nested (not inner) class does not take the star projection of its generic outer class.
// VARIABLE_PATH: x.v
// EVAL_EXPRESSION: (x as Nested<*>).v
// VARIABLE_PATH: y.w
// EVAL_EXPRESSION: (y as Nested).w
