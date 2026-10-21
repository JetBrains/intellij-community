// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

fun String.repeatText(count: Int): String = repeat(count)

class Example {
    companion {
        fun fromBlock(): String = "block".repeatText(2)
        fun fromImplicitReceiver(): String = with("implicit") { repeatText(4) }
    }
}

companion fun Example.fromExtension(): String = "extension".<caret>repeatText(count = 3)
