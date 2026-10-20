class Outer {
    companion {
        fun beta(x: Int): Int = x
    }

    class Inner {
        fun /*rename*/alpha(x: Int): Int = x
    }
}

fun outside(): Int = Outer.Inner().alpha(1)
