// NEW_NAME: existing1
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun existing1(): Int = 2 // DECL: 2
    }
}

val Example.<caret>original8: () -> Int get() = { 9 } // DECL: 1

fun useOriginal(): Int = Example().original8() // REF: 1

fun useExisting(): Int = Example.existing1() // REF: 2
