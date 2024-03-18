internal open class Base

internal class X : Base(), Cloneable {
    override fun hashCode(): Int {
        TODO()
    }

    override fun equals(o: Any?): Boolean {
        TODO()
    }

    override fun toString(): String {
        return super.toString()
    }

    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        TODO()
    }
}
