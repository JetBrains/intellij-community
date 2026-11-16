// ERROR: NULL_FOR_NONNULL_TYPE
// ERROR: NULL_FOR_NONNULL_TYPE
package demo

internal class Test : Iterable<String?> {
    override fun iterator(): MutableIterator<String?> {
        return null
    }

    fun push(i: MutableIterator<String?>?): MutableIterator<String?>? {
        val j = i
        return j
    }
}

internal class FullTest : Iterable<String?> {
    override fun iterator(): MutableIterator<String?> {
        return null
    }

    fun push(i: MutableIterator<String?>?): MutableIterator<String?>? {
        val j = i
        return j
    }
}
