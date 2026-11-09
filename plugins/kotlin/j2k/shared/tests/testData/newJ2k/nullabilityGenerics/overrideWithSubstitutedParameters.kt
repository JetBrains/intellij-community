internal open class A<T> {
    open fun foo(x: List<T?>?, y: Boolean?, z: T?) {
    }
}

internal class B : A<Int?>() {
    override fun foo(x: List<Int?>?, y: Boolean?, z: Int?) {
        if (z == null) return
    }
}
