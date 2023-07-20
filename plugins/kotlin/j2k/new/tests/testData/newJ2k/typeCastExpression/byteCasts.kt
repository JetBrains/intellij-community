internal class A {
    fun byteToInt() {
        val a = 1.toByte().toInt()
        val b: Byte = 2
        val c = b.toInt()
        val d = b + c
    }

    fun byteToChar() {
        val a = Char(1.toByte().toUShort())
        val b: Byte = 2
        val c = Char(b.toUShort())
        val d = (b + c.code.toByte()).toChar()
    }

    fun byteToShort() {
        val a = 1.toByte().toShort()
        val b: Byte = 2
        val c = b.toShort()
        val d = (b + c).toShort()
    }

    fun byteToLong() {
        val a = 1.toByte().toLong()
        val b: Byte = 2
        val c = b.toLong()
        val d = b + c
    }

    fun byteToFloat() {
        val a = 1.toByte().toFloat()
        val b: Byte = 2
        val c = b.toFloat()
        val d = b + c
    }

    fun byteToDouble() {
        val a = 1.toByte().toDouble()
        val b: Byte = 2
        val c = b.toDouble()
        val d = b + c
    }

    fun intToByte() {
        val a: Byte = 1
        val b = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun charToByte() {
        val a = 'a'.code.toByte()
        val b = 'b'
        val c = b.code.toByte()
        val d = (b.code.toByte() + c).toByte()
    }

    fun shortToByte() {
        val a = 1.toShort().toByte()
        val b: Short = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun longToByte() {
        val a = 1L.toByte()
        val b: Long = 2
        val c = b.toByte()
        val d = (b + c).toByte()
    }

    fun floatToByte() {
        val a = 1.0f.toInt().toByte()
        val b = 2f
        val c = b.toInt().toByte()
        val d = (b + c).toInt().toByte()
    }

    fun doubleToByte() {
        val a = 1.0.toInt().toByte()
        val b = 2.0
        val c = b.toInt().toByte()
        val d = (b + c).toInt().toByte()
    }
}
