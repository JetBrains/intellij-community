class Java8Class {
    fun foo0(r: Function0<String?>?) {
        TODO()
    }

    fun foo1(r: Function1<Int?, String?>?) {
        TODO()
    }

    fun foo2(r: Function2<Int?, Int?, String?>?) {
        TODO()
    }

    fun helper() {
        TODO()
    }

    fun foo() {
        foo0 { "42" }
        foo0 { "42" }
        foo0 {
            helper()
            "42"
        }

        foo1 { i: Int? -> "42" }
        foo1 { i: Int? -> "42" }
        foo1 { i: Int? ->
            helper()
            if (i!! > 1) {
                return@foo1 null
            }
            "43"
        }

        foo2 { i: Int?, j: Int? -> "42" }
        foo2 { i: Int?, j: Int? ->
            helper()
            "42"
        }

        val f: Function2<Int?, Int?, String?> = TODO()

        val f1: Function2<Int?, Int?, String?> = TODO()

        val runnable: Runnable = TODO()

        foo1 { i: Int? ->
            if (i!! > 1) {
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
    }
}
