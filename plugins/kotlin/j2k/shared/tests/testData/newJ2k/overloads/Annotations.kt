internal class A {
    @JvmOverloads
    fun foo(s: String? = null): Any {
        println(s)
        return ""
    }

    fun bar(s: String?): Any? {
        println(s)
        return if (s == null) "" else null
    }

    fun bar(): Any {
        return bar(null)!!
    }

    fun bar1(s: String?): Any? {
        println(s)
        return if (s == null) "" else null
    }

    fun bar1(): Any {
        return bar1(null)!!
    }

    @Deprecated("")
    fun f() {
        f(1)
    }

    fun f(p: Int) {
        println(p)
    }
}
