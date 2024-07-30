class TestInitInCtor(private var i: Int) {
    private val j: Int

    init {
        j = i
    }

    fun foo(): Int {
        return i + j
    }
}
