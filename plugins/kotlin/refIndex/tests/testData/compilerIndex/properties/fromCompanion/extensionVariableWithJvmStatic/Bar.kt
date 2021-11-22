import Main.Companion.companionExtensionProperty

class Bar {
    fun Main.test() {
        with(42) {
            companionExtensionProperty
        }
    }
}