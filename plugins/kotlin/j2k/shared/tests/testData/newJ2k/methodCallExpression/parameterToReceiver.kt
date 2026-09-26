import kotlinApi.extensionFunction

// !ADD_KOTLIN_API
object J {
    fun adjust(name: String?, maxLen: Int) {
        (1 + 1).toString()
        "a".split(("\\s+" + "\\s+").toRegex(), limit = 2).toTypedArray()
        (1 + 1).extensionFunction()
    }
}
