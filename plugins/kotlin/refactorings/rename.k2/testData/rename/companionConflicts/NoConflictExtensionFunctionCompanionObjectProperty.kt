// NEW_NAME: existing4
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        val existing4: () -> Int = { 5 } // DECL: 2
    }
}

fun Example.<caret>original9(): Int = 10 // DECL: 1

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.Companion.existing4() // REF: 2
