// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Label {
    companion {
    }
}

companion fun Label.<caret>render(prefix: String): String = "$prefix!"
