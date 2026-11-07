// ERROR: 'isSomething1' overrides nothing.
internal interface I {
    val isSomething1: Boolean

    val isSomething2: Boolean?

    val isSomething3: Int

    fun isSomething4(): Boolean
    fun setSomething4(value: Boolean)

    fun isSomething5(): Boolean
    fun setSomething5(value: Boolean)

    fun getSomething6(): Boolean
    fun setSomething6(value: Boolean)
}

internal abstract class C : I {
    override fun isSomething1(): Boolean {
        return true
    }

    fun setSomething1(b: Boolean) {
    }

    override fun isSomething4(): Boolean {
        return false
    }

    override fun setSomething5(value: Boolean) {
    }

    override fun setSomething6(value: Boolean) {
    }
}
