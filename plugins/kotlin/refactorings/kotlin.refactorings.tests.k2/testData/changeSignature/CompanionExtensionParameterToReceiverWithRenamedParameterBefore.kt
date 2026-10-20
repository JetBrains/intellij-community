// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Label {
    companion {
    }
}

class Target

companion fun Label.<caret>render(target: Target, value: String): String = value
