// ERROR: Primary constructor of data class must only have property ('val' / 'var') parameters.
// ERROR: Non-constructor properties with backing field in '@JvmRecord' class are prohibited.
class C {
    private val field = ArrayList<String>()

    fun foo() {
        D(field)
    }
}

@JvmRecord
internal data class D(param: ArrayList<String>) {
    val param: ArrayList<String>

    init {
        this.param = param
    }
}
