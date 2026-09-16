class Foo {
    companion object {
        val VALUE = 42
    }
}

fun main(args: Array<String>) {
    Foo.Companion./*rename*/VALUE
}
