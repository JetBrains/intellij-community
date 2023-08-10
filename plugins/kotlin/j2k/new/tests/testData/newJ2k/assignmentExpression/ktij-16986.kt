object Test {
    @JvmStatic
    fun main(args: Array<String>) {
        val a = longArrayOf(0)
        val b = 0
        a[0] += b.toLong()
    }
}
