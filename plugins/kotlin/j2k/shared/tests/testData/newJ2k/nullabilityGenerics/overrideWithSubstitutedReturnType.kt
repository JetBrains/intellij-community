internal open class A<T> {
    open fun foo(): Map<T?, List<T?>?>? {
        throw UnsupportedOperationException()
    }

    open fun bar(): T? {
        return null
    }
}

internal class B : A<Int?>() {
    override fun foo(): Map<Int?, List<Int?>?>? {
        return null
    }

    override fun bar(): Int {
        return 42
    }
}
