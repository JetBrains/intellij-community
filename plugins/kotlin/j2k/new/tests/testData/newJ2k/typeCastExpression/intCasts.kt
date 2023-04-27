internal class A {
    fun intToChar() {
        val a = 1.toChar()
        val b = 2
        val c = b.toChar()
        val d = (b + c.code).toChar()
    }

    fun intToByte() {
        val a: Byte = 1
        val b = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun intToShort() {
        val a: Short = 1
        val b = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun intToLong() {
        val a: Long = 1
        val b = 2
        val c = b.toLong()
        val d = b + c
    }

    fun intToFloat() {
        val a = 1f
        val b = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun intToDouble() {
        val a = 1.0
        val b = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun charToInt() {
        val a = 'a'.code
        val b = 'b'
        val c = b.code
        val d = b.code + c
    }

    fun byteToInt() {
        val a = 1.toByte().toInt()
        val b: Byte = 2
        val c = b.toInt()
        val d = b + c
    }

    fun shortToInt() {
        val a = 1.toShort().toInt()
        val b: Short = 2
        val c = b.toInt()
        val d = b + c
    }

    fun longToInt() {
        val a = 1L.toInt()
        val b: Long = 2
        val c = b.toInt()
        val d = (b + c).toInt()
    }

    fun floatToInt() {
        val a = 1.0f.toInt()
        val b = 2f
        val c = b.toInt()
        val d = (b + c).toInt()
    }

    fun doubleToInt() {
        val a = 1.0.toInt()
        val b = 2.0
        val c = b.toInt()
        val d = (b + c).toInt()
    }
}
