class Bar {
    private lateinit var foo: String

    fun baz(/*rename*/newFoo: String) {
        if(::foo.isInitialized) {
            throw Exception("AAA")
        }

        foo = newFoo
    }
}
