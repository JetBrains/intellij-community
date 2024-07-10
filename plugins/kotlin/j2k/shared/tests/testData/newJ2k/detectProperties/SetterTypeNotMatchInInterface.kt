internal interface I {
    val something: Int

    fun setSomething(value: String?)
}

internal class C : I {
    override val something: Int
        get() = 0

    override fun setSomething(value: String?) {
        println("set")
    }

    companion object {
        fun test(i: I) {
            println(i.something)
            i.setSomething("new")
        }
    }
}
