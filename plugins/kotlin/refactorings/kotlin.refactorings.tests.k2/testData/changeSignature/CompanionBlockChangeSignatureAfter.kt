// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example {
    companion {
        private fun repeatText(text: String, count: Int): String = text.repeat(count)
        fun fromBlock(): String = repeatText("block", 2)
    }

    fun fromInstance(): String = repeatText(text = "instance", count = 3)
}
