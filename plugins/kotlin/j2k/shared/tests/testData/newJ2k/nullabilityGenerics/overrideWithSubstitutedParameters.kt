internal open class A<T> {
    open fun foo(x: MutableList<T?>?, y: Boolean?, z: T?) {
    }
}

internal class B : A<Int?>() {
    override fun foo(x: MutableList<Int?>?, y: Boolean?, z: Int?) {
        if (z == null) return
    }
}
