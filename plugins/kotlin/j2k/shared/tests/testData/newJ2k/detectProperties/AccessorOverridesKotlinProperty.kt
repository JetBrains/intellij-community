object Test {
    fun test() {
        val foo = FooImpl()
        println(foo.a)
        foo.a = 42
    }
}
