// NEW_NAME: existing0
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        val existing0: () -> Int = { 1 } // DECL: 2
    }
}

fun Example.<caret>original9(): Int = 10 // DECL: 1

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.existing0() // REF: 2
