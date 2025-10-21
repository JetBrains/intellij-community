class Foo {
    companion object Foo {
        val VALUE = 42
    }
}

fun main(args: Array<String>) {
    println(Foo./*rename*/Foo.VALUE)
}