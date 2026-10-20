// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Label {
    companion {
        fun render(value: Int): String = value.toString()
        fun inBlock(): String = render(1)
    }
}

companion fun Label.<caret>render(prefix: String): String = ""
