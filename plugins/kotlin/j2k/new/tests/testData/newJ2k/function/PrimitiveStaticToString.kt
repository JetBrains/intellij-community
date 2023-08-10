// RUNTIME_WITH_FULL_JDK
class J {
    fun foo(bool: Boolean, c: Char, b: Byte, s: Short, i: Int, l: Long, f: Float, d: Double) {
        bool.toString()
        c.toString()
        Character.toString(i)
        b.toString()
        s.toString()
        i.toString()
        i.toString(i.coerceIn(2, 36))
        i.toString(2)
        i.toString(8)
        Integer.toString(i, 42.0.toInt()) // invalid code
        l.toString()
        l.toString(i.coerceIn(2, 36))
        l.toString(2)
        l.toString(8)
        java.lang.Long.toString(l, 42.0.toInt()) // invalid code
        f.toString()
        d.toString()
    }
}
