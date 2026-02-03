
fun foo(vararg names: String) {}

class Bar(vararg values: Int) {
    constructor(vararg values: Int, name: String): this(*values)

    fun bar(vararg names: String) {

    }
}