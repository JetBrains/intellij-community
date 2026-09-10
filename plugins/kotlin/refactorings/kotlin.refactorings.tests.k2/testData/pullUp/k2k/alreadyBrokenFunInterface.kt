fun interface FunInterface {
    fun foo(): String
    fun bar(): String
}

interface I<caret> : FunInterface {
    // INFO: {"checked": "true", "toAbstract": "true"}
    fun foobar(): String
}
