class Foo {
    companion object {
        val RENAMED_VALUE = 42
    }
}

fun main(args: Array<String>) {
    Foo.Companion.RENAMED_VALUE
}
