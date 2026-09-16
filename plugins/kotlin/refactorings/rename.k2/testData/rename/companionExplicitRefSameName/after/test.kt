class Foo {
    companion object Bar {
        val VALUE = 42
    }
}

fun main(args: Array<String>) {
    println(Foo.Bar.VALUE)
}