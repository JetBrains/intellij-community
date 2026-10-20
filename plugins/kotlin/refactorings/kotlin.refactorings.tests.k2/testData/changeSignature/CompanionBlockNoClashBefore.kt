// CHECK_ERRORS_AFTER
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Clash {
    companion {
        fun <caret>alpha(x: Int): Int = x
        fun beta(s: String): String = s
    }
}

fun outside(): Int = Clash.alpha(1)
