class Foo {
    internal class Bar {
        fun <T> bar(): Comparable<T?>? {
            return null
        }
    }

    private fun compare(o1: Bar?, o2: Bar?): Int {
        if (null == o1 || null == o1.bar<Any?>()) return -1
        if (o2 == null || o2.bar<Any?>() == null) return 1
        return o1.bar<Any?>()!!.compareTo(o2.bar<Any?>())
    }
}
