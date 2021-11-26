class Foo private constructor(name: String) {
    private companion object {
        private const val x: Long = 0
    }

    private class Nested private constructor(private val a: Int, private val u: UInt, private val l: Long, private val s: String) {
        private fun boo(): Nested {
            return Nested(a + s.length, u, l + 1, s)
        }
    }
}

fun <T> block(block: () -> T): T {
    return block()
}

fun main() {
    //Breakpoint!
    val x = 0
}

// Working as intended on EE-IR: No support for disabling reflective access

// REFLECTION_PATCHING: false

// EXPRESSION: block { Foo("foo") }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x") }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x").a }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x").u }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x").l }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x").s }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

// EXPRESSION: block { Foo.Nested(1, 0.toUInt(), -5, "x").boo() }
// RESULT: Method threw 'java.lang.VerifyError' exception.

// EXPRESSION: block { Foo.x }
// RESULT: 0: J

// EXPRESSION: block { Foo.x = 21 }
// RESULT: Method threw 'java.lang.IllegalAccessError' exception.

