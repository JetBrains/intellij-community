internal class A {
    fun charToInt() {
        val a = 'a'.code
        val b = 'b'
        val c = b.code
        val d = b.code + c
    }

    fun charToByte() {
        val a = 'a'.code.toByte()
        val b = 'b'
        val c = b.code.toByte()
        val d = (b.code.toByte() + c).toByte()
    }

    fun charToShort() {
        val a = 'a'.code.toShort()
        val b = 'b'
        val c = b.code.toShort()
        val d = (b.code.toShort() + c).toShort()
    }

    fun charToLong() {
        val a = 'a'.code.toLong()
        val b = 'b'
        val c = b.code.toLong()
        val d = b.code.toLong() + c
    }

    fun charToFloat() {
        val a = 'a'.code.toFloat()
        val b = 'b'
        val c = b.code.toFloat()
        val d = b.code.toFloat() + c
    }

    fun charToDouble() {
        val a = 'a'.code.toDouble()
        val b = 'b'
        val c = b.code.toDouble()
        val d = b.code.toDouble() + c
    }

    fun intToChar() {
        val a = 1.toChar()
        val b = 2
        val c = b.toChar()
        val d = (b + c.code).toChar()
    }

    fun byteToChar() {
        val a = Char(1.toByte().toUShort())
        val b: Byte = 2
        val c = Char(b.toUShort())
        val d = (b + c.code.toByte()).toChar()
    }

    fun shortToChar() {
        val a = Char(1.toShort().toUShort())
        val b: Short = 2
        val c = Char(b.toUShort())
        val d = (b + c.code.toShort()).toChar()
    }

    fun longToChar() {
        val a = Char(1L.toUShort())
        val b: Long = 2
        val c = Char(b.toUShort())
        val d = Char((b + c.code.toLong()).toUShort())
    }

    fun floatToChar() {
        val a = 1.0f.toInt().toChar()
        val b = 2f
        val c = b.toInt().toChar()
        val d = (b + c.code.toFloat()).toInt().toChar()
    }

    fun doubleToChar() {
        val a = 1.0.toInt().toChar()
        val b = 2.0
        val c = b.toInt().toChar()
        val d = (b + c.code.toDouble()).toInt().toChar()
    }
}