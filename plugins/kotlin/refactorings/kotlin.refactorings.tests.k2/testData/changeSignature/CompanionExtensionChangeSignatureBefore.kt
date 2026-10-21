// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example {
    companion {
        fun fromBlock(): String = repeatText(2, "block")
    }
}

companion fun Example.<caret>repeatText(times: Int, text: String): String = text.repeat(times)

companion fun Example.fromExtension(): String = repeatText(times = 3, text = "extension")

fun fromOutside(): String = Example.repeatText(4, "outside")
