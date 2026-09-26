object FooBar {
    @JvmStatic
    fun main(args: Array<String>) {
        println("aA".replace(("[a-z]" + "[A-Z]").toRegex(), "b"))
    }
}
