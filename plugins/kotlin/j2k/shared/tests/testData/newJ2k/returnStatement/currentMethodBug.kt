internal interface I {
    fun returnInt(): Int
}

internal class C {
    fun `object`(): Any? {
        foo(object : I {
            override fun returnInt(): Int {
                return 0
            }
        })
        return string
    }

    fun foo(i: I?) {}

    var string: String? = null
}
