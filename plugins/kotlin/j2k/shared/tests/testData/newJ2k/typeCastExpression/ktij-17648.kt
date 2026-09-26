class Foo {
    private val LONG_MASK: Long = 0

    private fun mulsubBorrow(q: IntArray, a: IntArray, x: Int, len: Int, offset: Int): Int {
        var offset = offset
        val xLong = x.toLong() and LONG_MASK
        var carry: Long = 0
        offset += len
        for (j in len - 1 downTo 0) {
            val product = (a[j].toLong() and LONG_MASK) * xLong + carry
            val difference = q[offset--] - product
            carry = ((product ushr 32)
                    + (if ((difference and LONG_MASK) >
                (((product.toInt().inv()).toLong() and LONG_MASK))
            ) 1 else 0))
        }
        return carry.toInt()
    }
}
