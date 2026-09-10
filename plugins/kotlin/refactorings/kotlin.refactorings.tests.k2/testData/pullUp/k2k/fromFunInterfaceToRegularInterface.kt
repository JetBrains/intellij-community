interface I

fun interface Fun<caret>Interface : I {
    // INFO: {"checked": "true", "toAbstract": "true"}
    fun foo(): String
}
