internal object SafeOpener {
    private fun showHeadersPopup() {
        val headersPopup: A<*> = object : A<P>() {
            override fun getTextFor(value: P): String {
                return "hi"
            }
        }
    }
}

internal abstract class B<T> {
    abstract fun getTextFor(value: T): String
}

internal open class A<T> : B<T>() {
    override fun getTextFor(value: T): String {
        return value.toString()
    }
}

internal class P
