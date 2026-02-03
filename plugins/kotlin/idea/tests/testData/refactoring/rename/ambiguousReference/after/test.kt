class Bar {
    private lateinit var foo: String

    fun baz(foo: String) {
        if(this::foo.isInitialized) {
            throw Exception("AAA")
        }

        this.foo = foo
    }
}
