internal object Test {
    fun getInt(i: Int): Int {
        when (i) {
            0 -> {
                println(0)
                return 0
            }

            1 -> {
                println(1)
                return 1
            }

            else -> return -1
        }
    }
}
