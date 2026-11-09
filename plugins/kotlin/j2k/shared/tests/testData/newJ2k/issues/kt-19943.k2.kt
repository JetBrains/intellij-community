class TestSpecialMethodForTypeValue {
    fun testByte(xs: List<Byte?>): Byte {
        return xs.get(0)!!
    }

    fun testShort(xs: List<Short?>): Short {
        return xs.get(0)!!
    }

    fun testInt(xs: List<Int?>): Int {
        return xs.get(0)!!
    }

    fun testLong(xs: List<Long?>): Long {
        return xs.get(0)!!
    }

    fun testFloat(xs: List<Float?>): Float {
        return xs.get(0)!!
    }

    fun testDouble(xs: List<Double?>): Double {
        return xs.get(0)!!
    }
}
