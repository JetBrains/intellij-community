class C {
    private val field = ArrayList<String>()

    fun foo() {
        D(field)
    }
}

@JvmRecord
internal data class D(val param: ArrayList<String>)
