class GClass<T> {
    fun foo(t: T): T {
        return t
    }

    override fun toString(): String {
        return "GClass()"
    }
}

val g = GClass::class

GClass<Int>().foo(1)