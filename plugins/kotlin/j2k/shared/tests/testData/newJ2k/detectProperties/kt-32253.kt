class TestInitInCtor(private val i: Int) {
    private val j = i

    fun foo(): Int {
        return i + j
    }
}
