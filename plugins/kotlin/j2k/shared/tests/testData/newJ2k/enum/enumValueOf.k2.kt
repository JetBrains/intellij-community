object Foo {
    @JvmStatic
    fun main(args: Array<String>) {
        enumValueOf<Color>("RED")
        foo(enumValueOf<Color>("RED"))
        enumValueOf<Color>("RED")
        foo(enumValueOf<Color>("RED"))
    }

    private fun foo(c: Color?) {
    }

    internal enum class Color {
        RED
    }
}
