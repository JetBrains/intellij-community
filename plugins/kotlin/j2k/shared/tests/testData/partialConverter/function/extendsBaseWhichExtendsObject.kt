internal class Test : Base() {
    override fun hashCode(): Int {
        TODO()
    }

    override fun equals(o: Any?): Boolean {
        TODO()
    }

    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        return super.clone()
    }

    override fun toString(): String {
        TODO()
    }

    @Throws(Throwable::class)
    override fun finalize() {
        TODO()
    }
}

internal open class Base : Cloneable {
    override fun hashCode(): Int {
        TODO()
    }

    override fun equals(o: Any?): Boolean {
        TODO()
    }

    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        TODO()
    }

    override fun toString(): String {
        TODO()
    }

    @Throws(Throwable::class)
    protected open fun finalize() {
        TODO()
    }
}
