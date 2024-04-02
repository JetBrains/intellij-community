internal class Foo {
    fun foo(a: Int, b: Int, s1: string) {
        var a = 0
        var b = 0
        var c = 0
        var d = 0
        val e = 0

        c = ((e.let { d *= it; d }))
        b += (c)
        a = b
        a = (((b)))
        a += (b)
        b = (c)
        a += b
        a = (c.let { b += it; (b) })

        val a = Char(1.toByte().toUShort())

        val x: List<String> = ArrayList()
        CollectionsKt.filter(x, object : Function1<String?, Boolean?>() {
            fun invoke(o: String): Boolean {
                return o == "a"
            }
        })

        if (0 == 1 && a.code > b) return
        if (0 == 1 &&  /*comment 1*/ /*comment 2*/a.code != b) return

        require(!(s1.length() < 3))

        bar(s1.toString() + "hello")

        s1.length()
        (s1.toString() + "postfix").length

        var z =
            1 +
                    2 +
                    3

        z =
            1 +
                    2 +
                    3

        println(1 shl 2 or 3)
        println(false or (true and (5 == 5 ushr 7)))
        val insideMultiline = (1
                + 1
                - 0x1234) and (0x1234 ushr 1)
    }

    fun bar(s: String?): Any? {
        println("s = $s")
        // TODO: add nested `x ? y : z`
        return if (s == null) "" else null
    }

    fun bar(): Any {
        return bar(null)!!
    }

    companion object {
        const val TEXT1: String = "text1.\n" +
                "text2\n" +
                "text3"
    }
}
