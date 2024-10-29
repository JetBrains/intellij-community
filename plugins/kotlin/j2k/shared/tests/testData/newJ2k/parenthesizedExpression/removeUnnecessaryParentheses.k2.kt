internal class Foo {
    fun foo(s1: String) {
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

        val ch = Char(1.toByte().toUShort())

        if (0 == 1 && a > b) return
        if (0 == 1 &&  /*comment 1*/ /*comment 2*/a != b) return

        require(s1.length >= 3)

        bar(s1 + "hello")

        s1.length
        (s1 + "postfix").length

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
        println("s = " + s)
        return if (s == null) "" else null
    }

    fun bar(): Any {
        return bar(null)!!
    }

    companion object {
        val TEXT1: String = "text1.\n" +
                "text2\n" +
                "text3"
    }
}
