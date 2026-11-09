internal interface A<T, S> {
    fun foo(): MutableList<MutableMap<T?, S?>?>?
}

internal class B<X> : A<X?, Int?> {
    override fun foo(): MutableList<MutableMap<X?, Int?>?>? {
        throw UnsupportedOperationException()
    }
}
