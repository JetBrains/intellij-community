object ArrayInitializerBugKt {
    private val GREETING = byteArrayOf(
        'H'.code.toByte(),
        'e'.code.toByte(),
        'l'.code.toByte(),
        'l'.code.toByte(),
        'o'.code.toByte(),
        ','.code.toByte(),
        ' '.code.toByte(),
        'b'.code.toByte(),
        'u'.code.toByte(),
        'g'.code.toByte(),
        '!'.code.toByte()
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val greeting = String(GREETING)
        println(greeting)
    }
}
