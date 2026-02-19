class J {
    fun test(s: String) {
        D(s)
    }
}

@JvmRecord
internal data class D(val s: String)
