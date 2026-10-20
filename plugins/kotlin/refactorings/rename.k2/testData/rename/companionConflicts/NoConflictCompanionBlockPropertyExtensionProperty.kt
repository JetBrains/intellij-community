// NEW_NAME: existing8
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        val <caret>original0: () -> Int = { 1 } // DECL: 1
    }
}

val Example.existing8: () -> Int get() = { 9 } // DECL: 2

fun useOriginal(): Int = Example.original0() // REF: 1

fun useExisting(): Int = Example().existing8() // REF: 2
