class A {
    var foo: String? = null

    fun g1(foo: String?): Boolean {
        return this.foo == foo
    }

    fun g2(foo: String?): Boolean {
        return this.foo == foo
    }

    fun g3(foo: String?, other: A): Boolean {
        return other.foo == foo
    }

    fun g4(foo: String?): Boolean {
        return this.foo.equals(foo, ignoreCase = true)
    }

    fun g5(foo: String?): Boolean {
        return this.foo.equals(foo, ignoreCase = true)
    }

    fun g6(foo: String?, other: A): Boolean {
        return other.foo.equals(foo, ignoreCase = true)
    }

    fun s1(foo: String?) {
        this.foo = foo
    }

    fun s2(foo: String?) {
        this.foo = foo
    }

    fun s3() {
        val foo = ""
        this.foo = foo
    }
}
