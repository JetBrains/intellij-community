fun interface FunInterface {
    fun foo(): String
}

interface I<caret> : FunInterface {
    // INFO: {"checked": "true", "toAbstract": "false"}
    val bar: Int
        get() = 42
}
