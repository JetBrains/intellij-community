// NEW_NAME: existing6
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    val existing6: () -> Int = { 7 } // DECL: 2

    companion {
        fun <caret>original1(): Int = 2 // DECL: 1
    }
}

fun useOriginal(): Int = Example.original1() // REF: 1
fun useExisting(): Int = Example().existing6() // REF: 2
