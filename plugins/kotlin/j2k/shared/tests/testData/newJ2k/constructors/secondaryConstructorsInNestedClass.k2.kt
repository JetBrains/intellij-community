// ERROR: Modifier 'protected' is not applicable inside 'standalone object'.
import Outer.Nested1
import Outer.Nested2
import Outer.Nested3
import Outer.Nested4

internal object Outer {
    fun foo() {
        val nested1 = Nested1(1)
        val nested2 = Nested2(2)
        val nested3 = Nested3(3)
        val nested4 = Nested4(4)
    }

    private class Nested1() {
        constructor(a: Int) : this()

        protected constructor(c: Char) : this()

        private constructor(b: Boolean) : this()
    }

    protected class Nested2() {
        constructor(a: Int) : this()

        protected constructor(c: Char) : this()

        private constructor(b: Boolean) : this()
    }

    internal class Nested3() {
        constructor(a: Int) : this()

        protected constructor(c: Char) : this()

        private constructor(b: Boolean) : this()
    }

    class Nested4() {
        constructor(a: Int) : this()

        protected constructor(c: Char) : this()

        private constructor(b: Boolean) : this()
    }
}
