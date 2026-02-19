internal class X : Cloneable {
    override fun hashCode(): Int {
        TODO()
    }

    override fun equals(o: Any?): Boolean {
        return super.equals(o)
    }

    override fun toString(): String {
        TODO()
    }

    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        TODO()
    }
}

internal class Y : Thread() {
    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        TODO()
    }
}
