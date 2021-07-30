import Main.Companion.topLevelExtension

fun test() {
    with(Main) {
        42.topLevelExtension()
    }
}