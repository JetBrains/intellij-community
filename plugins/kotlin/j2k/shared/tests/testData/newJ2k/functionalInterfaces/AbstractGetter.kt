fun interface MyRunnable {
    fun getResult(): Int

    val doubleResult: Int
        get() = getResult() * 2
}
