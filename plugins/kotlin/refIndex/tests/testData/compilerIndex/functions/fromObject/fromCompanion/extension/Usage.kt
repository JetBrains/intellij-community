import Main.Companion.extension

class Bar {
    fun Main.test() {
        with(42) {
            extension()
        }
    }
}