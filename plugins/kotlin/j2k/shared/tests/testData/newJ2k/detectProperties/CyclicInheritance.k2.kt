// ERROR: Cycle in supertypes and/or containing declarations detected.
// ERROR: Cycle in supertypes and/or containing declarations detected.
// ERROR: Cycle in supertypes and/or containing declarations detected.
// ERROR: Cycle in supertypes and/or containing declarations detected.
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

internal abstract class A : B(), I {
    open var isSomething1: Boolean
        get() = true
        set(b) {
        }

    val isSomething4: Boolean
        get() = false

    override fun setSomething5(value: Boolean) {
    }

    override fun setSomething6(value: Boolean) {
    }
}

internal abstract class B : A(), I {
    open var isSomething1: Boolean
        get() = true
        set(b) {
        }

    val isSomething4: Boolean
        get() = false

    override fun setSomething5(value: Boolean) {
    }

    override fun setSomething6(value: Boolean) {
    }
}
