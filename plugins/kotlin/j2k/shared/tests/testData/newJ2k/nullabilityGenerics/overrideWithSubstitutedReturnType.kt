internal open class A<T> {
    open fun foo(): MutableMap<T?, MutableList<T?>?>? {
        throw UnsupportedOperationException()
    }

    open fun bar(): T? {
        return null
    }
}

internal class B : A<Int?>() {
    override fun foo(): MutableMap<Int?, MutableList<Int?>?>? {
        return null
    }

    override fun bar(): Int {
        return 42
    }
}
