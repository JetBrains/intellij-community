// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Host {
    companion {
        fun inBlock(): Int = level()
    }
}

class OtherHost

companion fun Host.<caret>level(): Int = 42

fun outside(): Int = Host.level()
