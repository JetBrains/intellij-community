import Main.Companion.topLevelExtension

class Bar {
    fun Main.test() {
        with(42) {
            topLevelExtension()
        }
    }
}