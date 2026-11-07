// ERROR: Argument type mismatch: actual type is 'Int?', but 'Int' was expected.
internal class Test {
    fun <A, B> foo(value: A?, `fun`: FunctionalI<A?, B?>): B? {
        return `fun`.apply(value)
    }

    fun toDouble(x: Int): Double {
        return x.toDouble()
    }

    fun nya(): Double {
        return foo<Int?, Double?>(1, FunctionalI { x: kotlin.Int? -> this.toDouble(x) })!!
    }
}
