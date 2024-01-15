class Foo {
    fun doItNow() {
        doIt((if (1 > 2) 4 else 5).toFloat())
    }

    fun doIt(f: Float) {}
}
