internal open class Base {
    override fun equals(o: Any?): Boolean {
        TODO()
    }
}

internal class X : Base() {
    override fun equals(o: Any?): Boolean {
        return super.equals(o)
    }
}
