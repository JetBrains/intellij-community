package castToInnerClassInPackage

class GenericOuter<T> {
    inner class Inner(val s: String)
}

fun main() {
    val b: Any = GenericOuter<Int>().Inner("generic")
    //Breakpoint!
    println()
}

// IDEA-335226: in a package the generic outer class of an inner class gets its qualified name and the star projection.
// VARIABLE_PATH: b.s
// EVAL_EXPRESSION: (b as castToInnerClassInPackage.GenericOuter<*>.Inner).s
