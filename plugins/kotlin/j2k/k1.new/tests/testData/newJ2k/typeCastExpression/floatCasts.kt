internal class A {
    fun floatToChar() {
        val a = 1.0f.toInt().toChar()
        val b = 2f
        val c = b.toInt().toChar()
        val d = (b + c.code.toFloat()).toInt().toChar()
    }

    fun floatToByte() {
        val a = 1.0f.toInt().toByte()
        val b = 2f
        val c = b.toInt().toByte()
        val d = (b + c).toInt().toByte()
    }

    fun floatToShort() {
        val a = 1.0f.toInt().toShort()
        val b = 2f
        val c = b.toInt().toShort()
        val d = (b + c).toInt().toShort()
    }

    fun floatToInt() {
        val a = 1.0f.toInt()
        val b = 2f
        val c = b.toInt()
        val d = (b + c).toInt()
    }

    fun floatToLong() {
        val a = 1.0f.toLong()
        val b = 2f
        val c = b.toLong()
        val d = (b + c).toLong()
    }

    fun floatToDouble() {
        val a = 1.0
        val b = 2f
        val c = b.toDouble()
        val d = b + c
    }

    fun charToFloat() {
        val a = 'a'.code.toFloat()
        val b = 'b'
        val c = b.code.toFloat()
        val d = b.code.toFloat() + c
    }

    fun byteToFloat() {
        val a = 1.toByte().toFloat()
        val b: Byte = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun shortToFloat() {
        val a = 1.toShort().toFloat()
        val b: Short = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun intToFloat() {
        val a = 1f
        val b = 2
        val c = b.toFloat()
        val d = (b + c)
    }

    fun longToFloat() {
        val a = 1f
        val b: Long = 2
        val c = b.toFloat()
        val d = (b + c)
    }

    fun doubleToFloat() {
        val a = 1.0.toFloat()
        val b = 2.0
        val c = b.toFloat()
        val d = (b + c).toFloat()
    }
}
