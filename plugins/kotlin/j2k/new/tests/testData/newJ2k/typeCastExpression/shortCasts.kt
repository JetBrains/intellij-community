internal class A {
    fun shortToInt() {
        val a = 1.toShort().toInt()
        val b: Short = 2
        val c = b.toInt()
        val d = b + c
    }

    fun shortToChar() {
        val a = Char(1.toShort().toUShort())
        val b: Short = 2
        val c = Char(b.toUShort())
        val d = (b + c.code.toShort()).toChar()
    }

    fun shortToByte() {
        val a = 1.toShort().toByte()
        val b: Short = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun shortToLong() {
        val a = 1.toShort().toLong()
        val b: Short = 2
        val c = b.toLong()
        val d = b + c
    }

    fun shortToFloat() {
        val a = 1.toShort().toFloat()
        val b: Short = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun shortToDouble() {
        val a = 1.toShort().toDouble()
        val b: Short = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun intToShort() {
        val a: Short = 1
        val b = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun charToShort() {
        val a = 'a'.code.toShort()
        val b = 'b'
        val c = b.code.toShort()
        val d = (b.code.toShort() + c).toShort()
    }

    fun byteToShort() {
        val a = 1.toByte().toShort()
        val b: Byte = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun longToShort() {
        val a = 1L.toShort()
        val b: Long = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun floatToShort() {
        val a = 1.0f.toInt().toShort()
        val b = 2f
        val c = b.toInt().toShort()
        val d = (b + c).toInt().toShort()
    }

    fun doubleToShort() {
        val a = 1.0.toInt().toShort()
        val b = 2.0
        val c = b.toInt().toShort()
        val d = (b + c).toInt().toShort()
    }
}
