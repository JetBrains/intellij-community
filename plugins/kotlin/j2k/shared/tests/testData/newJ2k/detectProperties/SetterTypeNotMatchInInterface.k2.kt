// ERROR: 'getSomething' overrides nothing.
// ERROR: Class 'C' is not abstract and does not implement abstract member: val something: Int
internal interface I {
    val something: Int
    fun setSomething(value: String?)
}

internal class C : I {
    override fun getSomething(): Int {
        return 0
    }

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
