class Foo {
    fun f1(x: Int, y: Int): Boolean {
        return (x
                < y)
    }

    fun f2(x: Int, y: Int): Boolean {
        return if (false) {
            false
        } else (x
                < y)
    }
}
