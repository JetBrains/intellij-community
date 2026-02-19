class TestReturnsArray {
    fun strings(n: Int): Array<String?> {
        val result = arrayOfNulls<String>(n)
        for (i in 0..<n) {
            result[i] = i.toString()
        }
        return result
    }
}
