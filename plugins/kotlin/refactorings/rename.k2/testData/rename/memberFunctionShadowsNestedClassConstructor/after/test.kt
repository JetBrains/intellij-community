class Outer {
    class Helper(val x: Int)

    class Inner {
        fun Helper(x: Int) {}

        fun use(): Int = Outer.Helper(1).x
    }
}

fun outside() = Outer.Inner().Helper(1)
