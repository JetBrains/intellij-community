// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// CHECK_ERRORS_AFTER
class Outer {
    companion {
        fun beta(x: Int): Int = x
    }

    class Inner {
        fun beta(x: Int): Int = x
    }
}

fun outside(): Int = Outer.Inner().beta(1)
