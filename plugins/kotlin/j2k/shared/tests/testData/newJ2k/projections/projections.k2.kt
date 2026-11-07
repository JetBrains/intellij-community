internal class C<T> {
    fun foo1(src: MutableCollection<out T?>) {
        val t: T? = src.iterator().next()
    }

    fun foo2(src: ArrayList<out T?>) {
        val t: T? = src.iterator().next()
    }

    fun foo3(dst: MutableCollection<in T?>, t: T?) {
        dst.add(t)
    }

    fun foo4(comparable: Comparable<in T?>, t: T?): Int {
        return comparable.compareTo(t)
    }

    fun foo5(w: MutableCollection<*>?) {
    }
}
