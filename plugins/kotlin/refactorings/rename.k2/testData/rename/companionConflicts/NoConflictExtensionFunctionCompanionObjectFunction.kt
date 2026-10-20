// NEW_NAME: existing5
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun existing5(): Int = 6 // DECL: 2
    }
}

fun Example.<caret>original9(): Int = 10 // DECL: 1

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.Companion.existing5() // REF: 2
