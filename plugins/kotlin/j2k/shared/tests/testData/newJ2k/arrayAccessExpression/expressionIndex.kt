class J {
    fun test(array: IntArray) {
        val i = array[J().calculateIndex()]
    }

    fun calculateIndex(): Int {
        return 0
    }
}
