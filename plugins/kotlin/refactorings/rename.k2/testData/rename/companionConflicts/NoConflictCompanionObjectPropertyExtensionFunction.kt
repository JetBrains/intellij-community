// NEW_NAME: existing9
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        val <caret>original4: () -> Int = { 5 } // DECL: 1
    }
}

fun Example.existing9(): Int = 10 // DECL: 2

fun useOriginal(): Int = Example.Companion.original4() // REF: 1

fun useExisting(): Int = Example().existing9() // REF: 2
