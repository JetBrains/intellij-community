class Outer {
    companion {
        fun beta(x: Int): Int = x
    }

    class Inner {
        fun beta(x: Int): Int = x
    }
}

fun outside(): Int = Outer.Inner().beta(1)
