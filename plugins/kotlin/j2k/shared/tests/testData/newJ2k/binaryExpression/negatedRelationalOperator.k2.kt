object J {
    @JvmStatic
    fun main(args: Array<String>) {
        ieee(Double.Companion.NaN)
    }

    fun ieee(x: Double) {
        println(!(x <= 0))
    }
}
