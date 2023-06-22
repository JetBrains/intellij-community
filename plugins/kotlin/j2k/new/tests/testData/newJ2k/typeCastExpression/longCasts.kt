internal class A {
    fun longToChar() {
        val a = Char(1L.toUShort())
        val b: Long = 2
        val c = Char(b.toUShort())
        val d = Char((b + c.code.toLong()).toUShort())
    }

    fun longToByte() {
        val a = 1L.toByte()
        val b: Long = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun longToShort() {
        val a = 1L.toShort()
        val b: Long = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun longToInt() {
        val a = 1L.toInt()
        val b: Long = 2
        val c = b.toInt()
        val d = (b + c).toInt()
    }

    fun longToFloat() {
        val a = 1f
        val b: Long = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun longToDouble() {
        val a = 1.0
        val b: Long = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun charToLong() {
        val a = 'a'.code.toLong()
        val b = 'b'
        val c = b.code.toLong()
        val d = b.code.toLong() + c
    }

    fun byteToLong() {
        val a = 1.toByte().toLong()
        val b: Byte = 2
        val c = b.toLong()
        val d = b + c
    }

    fun shortToLong() {
        val a = 1.toShort().toLong()
        val b: Short = 2
        val c = b.toLong()
        val d = b + c
    }

    fun intToLong() {
        val a = 1.toLong()
        val b = 2
        val c = b.toLong()
        val d = b + c
    }

    fun floatToLong() {
        val a = 1.0f.toLong()
        val b = 2f
        val c = b.toLong()
        val d = (b + c).toLong()
    }

    fun doubleToLong() {
        val a = 1.0.toLong()
        val b = 2.0
        val c = b.toLong()
        val d = (b + c).toLong()
    }
}
