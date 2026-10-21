// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example {
    companion {
        fun <caret>repeatText(times: Int, text: String): String = text.repeat(times)
        fun fromBlock(): String = repeatText(2, "block")
    }

    fun fromInstance(): String = repeatText(times = 3, text = "instance")
}
