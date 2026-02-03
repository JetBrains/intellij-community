class TestNumberConversionsInTernary {
    fun intOrDoubleAsDouble(flag: Boolean, x: Int, y: Double): Double {
        val result = if (flag) x.toDouble() else y
        return result
    }
}
