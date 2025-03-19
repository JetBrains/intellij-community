internal object Foo {
    private val s: String? = null
    private val i: Int? = null
    private val c: Char? = null

    @JvmStatic
    fun main(args: Array<String>) {
        println("5" + 1)
        println(1.toString() + "5")
        println((1 + 3).toString() + "5")
        println((1 + 3).toString() + "5")
        println("5" + "5" + (1 + 3))
        println("5" + "5" + 1 + 3)
        println("5" + "5" + 1)
        println("5" + ("5" + 1) + 3)
        println("5" + ("5" + 1) + 3 + 4)
        println(1.toString() + "3" + 4 + "5")
        println((1 + 3 + 4).toString() + "5")
        println("5" + 1 + 3 + 4)
        println('c'.toString() + "5")
        println(('c'.code + 'd'.code).toString() + "5")
        println("5" + 'c')
        println("5" + 'c' + 'd')
        println(c.toString() + "s")
        println(c.toString() + "s" + c)
        println("s" + c + c)
        println(s + 'c')
        println(s + 'c' + 'd')
        println('c'.toString() + s)
        println(s + null)
        println(null.toString() + s)
        println(i.toString() + "s")
        println(i.toString() + "s" + i)
        println("s" + i + i)
        println(null.toString() + "s")
        println("s" + null)
        println("s" + null + null)
        val o = Any()
        println(o.toString() + "")
        println("" + o)
        println(o.hashCode().toString() + "")
        println("" + o.hashCode())
        val bar = arrayOf<String?>("hi")
        println(1.toString() + bar[0])
        println((1 + 2).toString() + bar[0])
        println(bar[0] + 1)
        println(bar[0] + 1 + 2)
    }
}
