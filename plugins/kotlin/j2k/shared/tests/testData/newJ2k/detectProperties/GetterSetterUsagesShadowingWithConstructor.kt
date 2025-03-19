class A(foo: String?) {
    var foo: String? = null

    init {
        this.foo = foo
        println(this.foo)
    }
}
