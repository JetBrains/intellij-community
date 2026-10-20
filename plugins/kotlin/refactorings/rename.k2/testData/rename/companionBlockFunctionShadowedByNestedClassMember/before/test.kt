class Outer {
    companion {
        fun beta(x: Int): Int = x
    }

    class Inner {
        fun /*rename*/alpha(x: Int): Int = x

        fun use(): Int = beta(1)
    }
}

fun outside(): Int = Outer.Inner().alpha(1)
