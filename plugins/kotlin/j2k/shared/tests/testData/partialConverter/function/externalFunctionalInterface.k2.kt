internal class Test {
    fun <A, B> foo(value: A?, `fun`: FunctionalI<A?, B?>): B? {
        TODO()
    }

    fun toDouble(x: Int): Double {
        TODO()
    }

    fun nya(): Double {
        return foo<Int?, Double?>(1, FunctionalI { x: Int? -> this.toDouble(x!!) })!!
    }
}
