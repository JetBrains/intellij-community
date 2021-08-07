internal class Test {
    fun operationsWithChar() {
        val c = 1.toChar()
        val i = 1
        b(i > c.code)
        b(i >= c.code)
        b(i < c.code)
        b(i <= c.code)
        b(c.code > i)
        b(c.code >= i)
        b(c.code < i)
        b(c.code <= i)
        b(c.code == i)
        b(c.code != i)
        b(i == c.code)
        b(i != c.code)
        i(i + c.code)
        i(i - c.code)
        i(i / c.code)
        i(i * c.code)
        i(i % c.code)
        i(i or c.code)
        i(i and c.code)
        i(i shl c.code)
        i(i shr c.code)
        i(c.code + i)
        i(c.code - i)
        i(c.code / i)
        i(c.code * i)
        i(c.code % i)
        i(c.code or i)
        i(c.code and i)
        i(c.code shl i)
        i(c.code shr i)
    }

    fun b(b: Boolean) {}
    fun i(i: Int) {}
}