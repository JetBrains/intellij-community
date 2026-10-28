// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// LANGUAGE_VERSION: 2.2

class Host

class Config {
    val value: Int = 42
}

context(c: Config)
companion fun Host.<caret>level(): Int = c.value
