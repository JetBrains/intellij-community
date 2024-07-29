// ERROR: Primary constructor of data class must only have property ('val' / 'var') parameters.
// ERROR: Non-constructor properties with backing field in '@JvmRecord' class are prohibited.
class J {
    fun test(s: String) {
        D(s)
    }
}

@JvmRecord
internal data class D(s: String) {
    val s: String

    init {
        this.s = s
    }
}
