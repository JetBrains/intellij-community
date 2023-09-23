object J {
    fun foo(bool: Boolean?, c: Char?, b: Byte?, s: Short?, i: Int?, l: Long?, f: Float?, d: Double?, obj: Any?) {
        println(bool)
        println(c)
        println(b)
        println(s)
        println(i)
        println(l)
        println(f)
        println(d)

        if (obj is Boolean) {
            println("Boolean: " + obj)
        } else if (obj is Char) {
            println("Character: " + obj)
        } else if (obj is Byte) {
            println("Byte: " + obj)
        } else if (obj is Short) {
            println("Short: " + obj)
        } else if (obj is Int) {
            println("Integer: " + obj)
        } else if (obj is Long) {
            println("Long: " + obj)
        } else if (obj is Float) {
            println("Float: " + obj)
        } else if (obj is Double) {
            println("Double: " + obj)
        }
    }
}
