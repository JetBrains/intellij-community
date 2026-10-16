// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

fun repeatText(text: String, count: Int): String = text.repeat(count)

class Example {
    companion {
        fun fromBlock(): String = repeatText("block", 2)
        fun fromImplicitReceiver(): String = with("implicit") { repeatText(this, 4) }
    }
}

companion fun Example.fromExtension(): String = repeatText(text = "extension", count = 3)
