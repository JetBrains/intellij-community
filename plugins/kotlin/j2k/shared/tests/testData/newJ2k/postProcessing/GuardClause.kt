class Test {
    fun testRequire(s1: String, b1: Boolean, b2: Boolean) {
        requireNotNull(s1) { "s should not be null" }

        require(b1)
        println("never mind")
        // comment above b2
        require(!b2)

        require(!(!b1 && b2))
        println(1)
        println(2)

        require(s1.length >= 3)
        if (s1.length == 4) {
            println(1)
        } else {
            println(2)
        }
    }

    fun testCheck(b: Boolean, notNullString: String) {
        check(!b)

        // comment above notNullString
        checkNotNull(notNullString)
    }

    fun testDoubles(x: Double, y: Double) {
        check(x < y)
        check(!(y < 2 * x))
    }

    fun doNotTouch(b: Boolean, s1: String) {
        try {
            println("hello!")
        } catch (e: Exception) {
            if (e is RuntimeException) {
                throw IllegalStateException(e)
            }
        }

        if (b) {
            throw IndexOutOfBoundsException()
        }

        if (s1.length < 5) {
            println("Some other side effect")
            throw IllegalStateException("oops")
        }
    }
}
