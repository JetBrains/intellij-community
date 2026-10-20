// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Label {
    companion {
    }
}

class Target

companion fun Target.<caret>render(label: Label, renamed: String): String = renamed
