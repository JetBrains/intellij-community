class Bar {
    companion object Foo {
        val VALUE = 42
    }
}

fun main(args: Array<String>) {
    println(Bar.VALUE)
}