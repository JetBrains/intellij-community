internal class A {
    fun doubleToChar() {
        val a = 1.0.toInt().toChar()
        val b = 2.0
        val c = b.toInt().toChar()
        val d = (b + c.code.toDouble()).toInt().toChar()
    }

    fun doubleToByte() {
        val a = 1.0.toInt().toByte()
        val b = 2.0
        val c = b.toInt().toByte()
        val d = (b + c).toInt().toByte()
    }

    fun doubleToShort() {
        val a = 1.0.toInt().toShort()
        val b = 2.0
        val c = b.toInt().toShort()
        val d = (b + c).toInt().toShort()
    }

    fun doubleToInt() {
        val a = 1.0.toInt()
        val b = 2.0
        val c = b.toInt()
        val d = (b + c).toInt()
    }

    fun doubleToLong() {
        val a = 1.0.toLong()
        val b = 2.0
        val c = b.toLong()
        val d = (b + c).toLong()
    }

    fun doubleToFloat() {
        val a = 1.0.toFloat()
        val b = 2.0
        val c = b.toFloat()
        val d = (b + c).toFloat()
    }

    fun charToDouble() {
        val a = 'a'.code.toDouble()
        val b = 'b'
        val c = b.code.toDouble()
        val d = b.code.toDouble() + c
    }

    fun byteToDouble() {
        val a = 1.toByte().toDouble()
        val b: Byte = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun shortToDouble() {
        val a = 1.toShort().toDouble()
        val b: Short = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun intToDouble() {
        val a = 1.0
        val b = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun longToDouble() {
        val a = 1.0
        val b: Long = 2
        val c = b.toDouble()
        val d = (b + c)
    }

    fun floatToDouble() {
        val a = 1.0
        val b = 2f
        val c = b.toDouble()
        val d = b + c
    }
}
