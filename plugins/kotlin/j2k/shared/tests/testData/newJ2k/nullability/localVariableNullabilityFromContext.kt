// !SPECIFY_LOCAL_VARIABLE_TYPE_BY_DEFAULT: true
class Foo {
    fun test() {
        val i: Int = J().getInt() // initialized as a platform type
        println(i + i) // used as a not-null type

        val j: Int? = J().getInt()
        if (j != null) {
            println(j + j)
        }
    }
}
