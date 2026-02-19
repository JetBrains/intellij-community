// KTIJ-29632
package demo

import demo.Foo.Bar

internal class Foo {
    internal class Bar
}

internal class User {
    fun main() {
        val boo: Bar = Bar()
    }
}
