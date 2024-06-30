// KTIJ-29632
package demo

internal class Foo {
    internal class Bar
}

internal class User {
    fun main() {
        val boo = Foo.Bar()
    }
}
