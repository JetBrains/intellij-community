class TestSpecialMethodForTypeValue {
    fun testByte(xs: MutableList<Byte?>): Byte {
        return xs.get(0)!!
    }

    fun testShort(xs: MutableList<Short?>): Short {
        return xs.get(0)!!
    }

    fun testInt(xs: MutableList<Int?>): Int {
        return xs.get(0)!!
    }

    fun testLong(xs: MutableList<Long?>): Long {
        return xs.get(0)!!
    }

    fun testFloat(xs: MutableList<Float?>): Float {
        return xs.get(0)!!
    }

    fun testDouble(xs: MutableList<Double?>): Double {
        return xs.get(0)!!
    }
}
