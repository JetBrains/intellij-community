class TestInitInCtor(private val i: Int) {
    private val j: Int

    init {
        j = i
    }

    fun foo(): Int {
        return i + j
    }
}
