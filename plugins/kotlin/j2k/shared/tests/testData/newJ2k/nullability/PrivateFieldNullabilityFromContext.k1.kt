class Foo {
    private val i: Int = J().int // initialized as a platform type
    private val j: Int = J().int // initialized as a platform type
    private val k: Int? = J().int
    private val l: Int? = J().int

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
