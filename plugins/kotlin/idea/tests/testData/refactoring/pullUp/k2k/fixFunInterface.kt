fun interface FunInterface

interface I<caret> : FunInterface {
    // INFO: {"checked": "true", "toAbstract": "true"}
    fun foo(): String
}
