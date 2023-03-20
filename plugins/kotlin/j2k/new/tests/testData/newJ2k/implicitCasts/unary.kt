class J {
    fun foo(b: Byte, c: Char, s: Short, i: Int, l: Long, f: Float, d: Double) {
        i(-b.toInt())
        i(+b.toInt())
        i(b.toInt().inv())
        i(-c.code)
        i(+c.code)
        i(c.code.inv())
        i(-s.toInt())
        i(+s.toInt())
        i(s.toInt().inv())
        i(-i)
        i(+i)
        i(i.inv())
        l(-l)
        l(+l)
        l(l.inv())
        f(-f)
        f(+f)
        d(-d)
        d(+d)
    }

    fun i(i: Int) {}
    fun l(l: Long) {}
    fun f(f: Float) {}
    fun d(d: Double) {}
}
