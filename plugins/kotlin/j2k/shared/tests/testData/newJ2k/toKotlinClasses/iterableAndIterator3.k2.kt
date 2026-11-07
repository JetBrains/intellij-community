// ERROR: Null cannot be a value of a non-null type 'MutableIterator<String?>'.
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
