// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example {
    companion {
        fun repeatText(): String {
            return repeatLocal("block", 2)
        }

        fun <caret>repeatLocal(text: String, count: Int): String = text.repeat(count)
    }
}
