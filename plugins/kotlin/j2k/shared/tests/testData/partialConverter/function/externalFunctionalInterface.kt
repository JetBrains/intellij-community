internal class Test {
    fun <A, B> foo(value: A, `fun`: FunctionalI<A, B>): B {
        TODO()
    }

    fun toDouble(x: Int): Double {
        TODO()
    }

    fun nya(): Double {
        return foo(1) { x: Int -> this.toDouble(x) }
    }
}
