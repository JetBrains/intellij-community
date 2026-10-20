class Outer {
    companion {
        fun beta(x: Int): Int = x
    }

    class Inner {
        fun beta(x: Int): Int = x

        fun use(): Int = Outer.beta(1)
    }
}

fun outside(): Int = Outer.Inner().beta(1)
