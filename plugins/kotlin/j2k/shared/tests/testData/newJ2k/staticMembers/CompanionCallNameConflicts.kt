class CompanionCallNameConflicts {
    fun callFromClass(x: Int): Int {
        return twice(x)
    }

    fun log(o: Any?) {
    }

    fun callOverloadedByInstanceMethod() {
        Companion.log(1)
    }

    internal class Nested {
        fun callFromNested(): Int {
            return twice(1)
        }
    }

    fun <Int> typeParameterNamedInt(value: Int?, count: kotlin.Int): Int? {
        return value
    }

    companion object {
        fun twice(x: Int): Int {
            return inc(inc(x))
        }

        fun inc(x: Int): Int {
            return x + 1
        }

        fun log(i: Int) {
        }
    }
}
