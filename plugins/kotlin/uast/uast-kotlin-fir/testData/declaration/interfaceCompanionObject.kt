package test.pkg

interface Foo {
    companion object {
        @JvmField
        const val answer: Int = 42
        @JvmStatic
        fun sayHello() {
            println("Hello, world!")
        }
    }
}