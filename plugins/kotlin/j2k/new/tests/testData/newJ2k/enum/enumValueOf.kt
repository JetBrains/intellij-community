object Foo {
    @JvmStatic
    fun main(args: Array<String>) {
        enumValueOf<Color>("RED")
        foo(enumValueOf("RED"))
        enumValueOf<Color>("RED")
        foo(enumValueOf("RED"))
    }

    private fun foo(c: Color) {}
    internal enum class Color {
        RED
    }
}
