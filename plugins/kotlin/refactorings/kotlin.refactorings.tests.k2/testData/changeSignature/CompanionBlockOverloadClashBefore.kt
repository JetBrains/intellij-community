// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Clash {
    companion {
        fun alpha(x: Int): Int = x
        fun <caret>alpha(s: String): Int = s.length
    }
}

fun outside(): Int = Clash.alpha("text")
