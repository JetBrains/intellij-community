package test.pkg

interface Foo {
    companion object {
        @JvmField
        val answer: Int = 42
        @JvmStatic
        fun sayHello() {
            println("Hello, world!")
        }
    }
}