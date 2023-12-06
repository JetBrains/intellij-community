object Test {
    private fun foo(text: String?): Boolean {
        return text == null ||
                (text.length != 2 && text.length != 3 && asFoo(text) != Foo.BAD) || asFoo(text) == Foo.GOOD
    }

    private fun asFoo(text: String): Foo {
        return if (text.isEmpty()) Foo.BAD else Foo.GOOD
    }

    internal enum class Foo {
        GOOD, BAD
    }
}
