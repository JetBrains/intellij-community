// ERROR: No set method providing array access
// ERROR: No set method providing array access
// ERROR: No set method providing array access
// ERROR: No set method providing array access
// ERROR: No set method providing array access
class J {
    fun testField(c: Char, b: Byte, s: Short, i: Int, l: Long, f: Float, d: Double) {
        var cc = 1.toChar()
        cc += c.code
        cc += Char(b.toUShort()).code
        cc += Char(s.toUShort()).code
        cc += i.toChar().code
        cc += Char(l.toUShort()).code
        cc = (cc.code.toFloat() + f).toInt().toChar()
        cc = (cc.code.toDouble() + d).toInt().toChar()
        cc = (cc.code.toDouble() - d).toInt().toChar()
        cc = (cc.code.toFloat() * f).toInt().toChar()
        cc = (cc.code.toDouble() / d).toInt().toChar()
        cc = (cc.code.toDouble() % (f + d + f)).toInt().toChar()
        //
        var bb: Byte = 1
        bb = (bb + c.code.toByte()).toByte()
        bb = (bb + b).toByte()
        bb = (bb + s).toByte()
        bb = (bb + i).toByte()
        bb = (bb + l).toByte()
        bb = (bb + f).toInt().toByte()
        bb = (bb + d).toInt().toByte()
        bb = (bb - d).toInt().toByte()
        bb = (bb * f).toInt().toByte()
        bb = (bb / d).toInt().toByte()
        bb = (bb % (f + d + f)).toInt().toByte()
        //
        var ss: Short = 1
        ss = (ss + c.code.toShort()).toShort()
        ss = (ss + b).toShort()
        ss = (ss + s).toShort()
        ss = (ss + i).toShort()
        ss = (ss + l).toShort()
        ss = (ss + f).toInt().toShort()
        ss = (ss + d).toInt().toShort()
        ss = (ss - d).toInt().toShort()
        ss = (ss * f).toInt().toShort()
        ss = (ss / d).toInt().toShort()
        ss = (ss % (f + d + f)).toInt().toShort()
        //
        var ii = 1
        ii += c.code
        ii += b.toInt()
        ii += s.toInt()
        ii += i
        ii += l.toInt()
        ii = (ii + f).toInt()
        ii = (ii + d).toInt()
        ii = (ii - d).toInt()
        ii = (ii * f).toInt()
        ii = (ii / d).toInt()
        ii = (ii % (f + d + f)).toInt()
        //
        var ll: Long = 1
        ll += c.code.toLong()
        ll += b.toLong()
        ll += s.toLong()
        ll += i.toLong()
        ll += l
        ll = (ll + f).toLong()
        ll = (ll + d).toLong()
        ll = (ll - d).toLong()
        ll = (ll * f).toLong()
        ll = (ll / d).toLong()
        ll = (ll % (f + d + f)).toLong()
        //
        var ff = 1f
        ff += c.code.toFloat()
        ff += b.toFloat()
        ff += s.toFloat()
        ff += i.toFloat()
        ff += l.toFloat()
        ff += f
        ff += d.toFloat()
        ff -= d.toFloat()
        ff *= f
        ff /= d.toFloat()
        ff %= (f + d + f).toFloat()
        //
        var dd = 1.0
        dd += c.code.toDouble()
        dd += b.toDouble()
        dd += s.toDouble()
        dd += i.toDouble()
        dd += l.toDouble()
        dd += f.toDouble()
        dd += d
        dd -= d
        dd *= f.toDouble()
        dd /= d
        dd %= f + d + f
    }

    fun testArrayAccess(c: Char, b: Byte, s: Short, i: Int, l: Long, f: Float, d: Double) {
        // in K1 this currently results in a NO_SET_METHOD error (KT-11272), which should be fixed in K2
        val charArr = charArrayOf(1.toChar())
        // <KT-11272>
        charArr[0] += c.code
        charArr[0] += Char(b.toUShort()).code
        charArr[0] += Char(s.toUShort()).code
        charArr[0] += i.toChar().code
        charArr[0] += Char(l.toUShort()).code
        // </KT-11272>
        charArr[0] = (charArr[0].code.toFloat() + f).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() + d).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() - d).toInt().toChar()
        charArr[0] = (charArr[0].code.toFloat() * f).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() / d).toInt().toChar()
        charArr[0] = (charArr[0].code.toDouble() % (f + d + f)).toInt().toChar()
        //
        val byteArr = byteArrayOf(1)
        byteArr[0] = (byteArr[0] + c.code.toByte()).toByte()
        byteArr[0] = (byteArr[0] + b).toByte()
        byteArr[0] = (byteArr[0] + s).toByte()
        byteArr[0] = (byteArr[0] + i).toByte()
        byteArr[0] = (byteArr[0] + l).toByte()
        byteArr[0] = (byteArr[0] + f).toInt().toByte()
        byteArr[0] = (byteArr[0] + d).toInt().toByte()
        byteArr[0] = (byteArr[0] - d).toInt().toByte()
        byteArr[0] = (byteArr[0] * f).toInt().toByte()
        byteArr[0] = (byteArr[0] / d).toInt().toByte()
        byteArr[0] = (byteArr[0] % (f + d + f)).toInt().toByte()
        //
        val shortArr = shortArrayOf(1)
        shortArr[0] = (shortArr[0] + c.code.toShort()).toShort()
        shortArr[0] = (shortArr[0] + b).toShort()
        shortArr[0] = (shortArr[0] + s).toShort()
        shortArr[0] = (shortArr[0] + i).toShort()
        shortArr[0] = (shortArr[0] + l).toShort()
        shortArr[0] = (shortArr[0] + f).toInt().toShort()
        shortArr[0] = (shortArr[0] + d).toInt().toShort()
        shortArr[0] = (shortArr[0] - d).toInt().toShort()
        shortArr[0] = (shortArr[0] * f).toInt().toShort()
        shortArr[0] = (shortArr[0] / d).toInt().toShort()
        shortArr[0] = (shortArr[0] % (f + d + f)).toInt().toShort()
        //
        val intArr = intArrayOf(1)
        intArr[0] += c.code
        intArr[0] += b.toInt()
        intArr[0] += s.toInt()
        intArr[0] += i
        intArr[0] += l.toInt()
        intArr[0] = (intArr[0] + f).toInt()
        intArr[0] = (intArr[0] + d).toInt()
        intArr[0] = (intArr[0] - d).toInt()
        intArr[0] = (intArr[0] * f).toInt()
        intArr[0] = (intArr[0] / d).toInt()
        intArr[0] = (intArr[0] % (f + d + f)).toInt()
        //
        val longArr = longArrayOf(1)
        longArr[0] += c.code.toLong()
        longArr[0] += b.toLong()
        longArr[0] += s.toLong()
        longArr[0] += i.toLong()
        longArr[0] += l
        longArr[0] = (longArr[0] + f).toLong()
        longArr[0] = (longArr[0] + d).toLong()
        longArr[0] = (longArr[0] - d).toLong()
        longArr[0] = (longArr[0] * f).toLong()
        longArr[0] = (longArr[0] / d).toLong()
        longArr[0] = (longArr[0] % (f + d + f)).toLong()
        //
        val floatArr = floatArrayOf(1f)
        floatArr[0] += c.code.toFloat()
        floatArr[0] += b.toFloat()
        floatArr[0] += s.toFloat()
        floatArr[0] += i.toFloat()
        floatArr[0] += l.toFloat()
        floatArr[0] += f
        floatArr[0] += d.toFloat()
        floatArr[0] -= d.toFloat()
        floatArr[0] *= f
        floatArr[0] /= d.toFloat()
        floatArr[0] %= (f + d + f).toFloat()
        //
        val doubleArr = doubleArrayOf(1.0)
        doubleArr[0] += c.code.toDouble()
        doubleArr[0] += b.toDouble()
        doubleArr[0] += s.toDouble()
        doubleArr[0] += i.toDouble()
        doubleArr[0] += l.toDouble()
        doubleArr[0] += f.toDouble()
        doubleArr[0] += d
        doubleArr[0] -= d
        doubleArr[0] *= f.toDouble()
        doubleArr[0] /= d
        doubleArr[0] %= f + d + f
    }
}
