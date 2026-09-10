package a

fun <caret>foo() {

    val p = mutableListOf<Param>()

    fun bar() {
        p += Param("a")
    }
}

class Param(symbol: String)