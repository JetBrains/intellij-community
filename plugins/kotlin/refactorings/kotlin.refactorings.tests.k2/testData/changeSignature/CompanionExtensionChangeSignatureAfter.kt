// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example {
    companion {
        fun fromBlock(): String = repeatText("block", 2)
    }
}

private companion fun Example.repeatText(text: String, count: Int): String = text.repeat(count)

companion fun Example.fromExtension(): String = repeatText(text = "extension", count = 3)

fun fromOutside(): String = Example.repeatText("outside", 4)
