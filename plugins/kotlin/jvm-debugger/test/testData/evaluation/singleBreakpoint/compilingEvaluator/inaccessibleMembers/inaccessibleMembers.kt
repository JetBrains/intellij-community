package test

class Foo private constructor(name: String) {
    private companion object {
        private const val x: Long = 0
    }

    class Nested(private val a: Int, private val l: Long, private val s: String) {
        private fun boo(): Nested {
            return Nested(a + s.length, l + 1, s)
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

// EXPRESSION: block { Foo("foo") }
// RESULT: instance of test.Foo(id=ID): Ltest/Foo;

// EXPRESSION: block { Foo.Nested(1, -5, "x") }
// RESULT: instance of test.Foo$Nested(id=ID): Ltest/Foo$Nested;

// EXPRESSION: block { Foo.Nested(1, -5, "x").a }
// RESULT: 1: I

// EXPRESSION: block { Foo.Nested(1, -5, "x").l }
// RESULT: -5: J

// EXPRESSION: block { Foo.Nested(1, -5, "x").s }
// RESULT: "x": Ljava/lang/String;

// EXPRESSION: block { Foo.Nested(1, -5, "x").boo() }
// RESULT: instance of test.Foo$Nested(id=ID): Ltest/Foo$Nested;

// EXPRESSION: block { Foo.x }
// RESULT: 0: J

// EXPRESSION: block { Foo.x = 21 }
// RESULT: Method threw 'java.lang.IllegalAccessException' exception.