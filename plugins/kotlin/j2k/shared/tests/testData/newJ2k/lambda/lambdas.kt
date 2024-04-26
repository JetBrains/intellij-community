class Java8Class {
    fun foo0(r: Function0<String?>?) {
    }

    fun foo1(r: Function1<Int, String?>?) {
    }

    fun foo2(r: Function2<Int?, Int?, String?>?) {
    }

    fun foo3(r1: Function0<String?>?, r2: Function0<String?>?) {
    }

    @JvmOverloads
    fun bar(f: Function0<String?>?, g: Function1<Int?, String?>? = { i: Int? -> "default g" }) {
    }

    fun doNotTouch(f: Function0<String?>?, s: String?) {
    }

    fun helper() {
    }

    fun vararg(key: String?, vararg functions: Function0<String?>?) {
    }

    internal open inner class Base(name: String?, f: Function0<String?>?)

    internal inner class Child : Base("Child", { "a child class" })

    fun foo() {
        foo0 { "42" }
        foo0 { "42" }
        foo0 {
            helper()
            "42"
        }

        foo1 { i: Int? -> "42" }
        foo1 { i: Int? -> "42" }
        foo1 { i: Int ->
            helper()
            if (i > 1) {
                return@foo1 null
            }
            "43"
        }

        foo2 { i: Int?, j: Int? -> "42" }
        foo2 { i: Int?, j: Int? ->
            helper()
            "42"
        }

        foo3({ "42" }, { "42" })

        bar({ "f" }, { i: Int? -> "g" })

        checkNotNull("s") { "that's strange" }

        val base = Base("Base") { "base" }

        vararg("first", { "f" })

        runnableFun { "hello" }

        val f: Function2<Int, Int, String> = label@{ i: Int, k: Int? ->
            helper()
            if (i > 1) {
                return@label "42"
            }
            "43"
        }

        val f1 = label@{ i1: Int, k1: Int ->
            val f2: Function2<Int, Int, String> = label@{ i2: Int, k2: Int? ->
                helper()
                if (i2 > 1) {
                    return@label "42"
                }
                "43"
            }
            if (i1 > 1) {
                return@label f.invoke(i1, k1)
            }
            f.invoke(i1, k1)
        }

        val runnable1 = Runnable {}

        val runnable2 = Runnable {
            if (true) return@Runnable
            println("false")
        }

        foo1 { i: Int ->
            if (i > 1) {
                return@foo1 "42"
            }
            foo0 {
                if (true) {
                    return@foo0 "42"
                }
                "43"
            }
            "43"
        }

        doNotTouch({ "first arg" }, "last arg")
    }

    fun moreTests(
        m1: MutableMap<String?, String?>,
        m2: MutableMap<String?, String?>,
        m3: MutableMap<String?, String?>,
        m4: MutableMap<String?, String>
    ) {
        m1.compute("m1") { k: String?, v: String? -> v }
        m2.computeIfAbsent("m2") { k: String? -> "value" }
        m3.computeIfPresent("m1") { k: String?, v: String? -> v }
        m4.merge("", "") { k: String?, v: String? -> v }
        m4.merge("", "") { obj: String, str: String -> obj + str }

        val ss = Array(5) { arrayOfNulls<String>(5) }

        val s = "test"
        s.trim { it <= ' ' }
    }

    companion object {
        fun runnableFun(r: Runnable?) {}
    }
}
