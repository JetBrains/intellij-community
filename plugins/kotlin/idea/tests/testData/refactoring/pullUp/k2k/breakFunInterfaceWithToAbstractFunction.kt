// IGNORE_K1

fun interface FunInterface {
    fun foo(): String
}

interface I<caret> : FunInterface {
    // INFO: {"checked": "true", "toAbstract": "true"}
    fun bar(): String = ""
}
