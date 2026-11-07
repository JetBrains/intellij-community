// ERROR: Return type mismatch: expected 'Array<IntArray>', actual 'Array<IntArray?>?'.
internal object A {
    fun sampleFunction(a: Array<IntArray?>?, b: Array<IntArray?>?): Array<IntArray> {
        val n = a!![0]!!.size
        val m = a.size
        val p = b!![0]!!.size
        val result: Array<IntArray?>? = Array<IntArray?>(m) { IntArray(p) }
        for (i in 0..<m) {
            for (j in 0..<p) {
                for (k in 0..<n) {
                    result!![i]!![j] += a[i]!![k] * b[k]!![j]
                }
            }
        }
        return result
    }
}
