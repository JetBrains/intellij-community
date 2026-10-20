class Outer {
    class Helper(val x: Int)

    class Inner {
        fun /*rename*/alpha(x: Int) {}

        fun use(): Int = Helper(1).x
    }
}

fun outside() = Outer.Inner().alpha(1)
