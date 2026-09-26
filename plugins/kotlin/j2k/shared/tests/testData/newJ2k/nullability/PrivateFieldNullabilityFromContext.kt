class Foo {
    private val i: Int = J().getInt() // initialized as a platform type
    private val j: Int
    private val k: Int? = J().getInt()
    private val l: Int?

    init {
        this.j = J().getInt() // initialized as a platform type
        this.l = J().getInt()
    }

    fun test() {
        println(i + i) // used as a not-null type
        println(j + j) // used as a not-null type

        if (k != null) {
            println(k + k)
        }

        if (l != null) {
            println(l + l)
        }
    }
}
