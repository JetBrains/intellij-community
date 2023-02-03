// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toByte(): Byte' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Byte.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
// ERROR: Using 'toShort(): Short' is an error. Unclear conversion. To achieve the same result convert to Int explicitly and then to Short.
class J {
    fun testField(f: Float, d: Double) {
        var c = 1.toChar()
        c = (c.code.toFloat() + f).toInt().toChar()
        c = (c.code.toDouble() - d).toInt().toChar()
        c = (c.code.toFloat() * f).toInt().toChar()
        c = (c.code.toDouble() / d).toInt().toChar()
        c = (c.code.toDouble() % (f + d + f)).toInt().toChar()

        // TODO KTIJ-24547
        var b: Byte = 1
        b = (b + f).toByte()
        b = (b - d).toByte()
        b = (b * f).toByte()
        b = (b / d).toByte()
        b = (b % (f + d + f)).toByte()

        // TODO KTIJ-24547
        var s: Short = 1
        s = (s + f).toShort()
        s = (s - d).toShort()
        s = (s * f).toShort()
        s = (s / d).toShort()
        s = (s % (f + d + f)).toShort()
        var i = 1
        i = (i + f).toInt()
        i = (i - d).toInt()
        i = (i * f).toInt()
        i = (i / d).toInt()
        i = (i % (f + d + f)).toInt()
        var l: Long = 1
        l = (l + f).toLong()
        l = (l - d).toLong()
        l = (l * f).toLong()
        l = (l / d).toLong()
        l = (l % (f + d + f)).toLong()
    }

    fun testArrayAccess(f: Float, d: Double) {
        val charArr = charArrayOf(1.toChar())
        charArr[0] = (charArr[0].code.toFloat() + f).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() - d).toInt().toChar()
        charArr[0] = (charArr[0].code.toFloat() * f).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() / d).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() % (f + d + f)).toInt().toChar()

        // TODO KTIJ-24547
        val byteArr = byteArrayOf(1)
        byteArr[0] = (byteArr[0] + f).toByte()
        byteArr[0] = (byteArr[0] - d).toByte()
        byteArr[0] = (byteArr[0] * f).toByte()
        byteArr[0] = (byteArr[0] / d).toByte()
        byteArr[0] = (byteArr[0] % (f + d + f)).toByte()

        // TODO KTIJ-24547
        val shortArr = shortArrayOf(1)
        shortArr[0] = (shortArr[0] + f).toShort()
        shortArr[0] = (shortArr[0] - d).toShort()
        shortArr[0] = (shortArr[0] * f).toShort()
        shortArr[0] = (shortArr[0] / d).toShort()
        shortArr[0] = (shortArr[0] % (f + d + f)).toShort()
        val intArr = intArrayOf(1)
        intArr[0] = (intArr[0] + f).toInt()
        intArr[0] = (intArr[0] - d).toInt()
        intArr[0] = (intArr[0] * f).toInt()
        intArr[0] = (intArr[0] / d).toInt()
        intArr[0] = (intArr[0] % (f + d + f)).toInt()
        val longArr = longArrayOf(1)
        longArr[0] = (longArr[0] + f).toLong()
        longArr[0] = (longArr[0] - d).toLong()
        longArr[0] = (longArr[0] * f).toLong()
        longArr[0] = (longArr[0] / d).toLong()
        longArr[0] = (longArr[0] % (f + d + f)).toLong()
    }
}
