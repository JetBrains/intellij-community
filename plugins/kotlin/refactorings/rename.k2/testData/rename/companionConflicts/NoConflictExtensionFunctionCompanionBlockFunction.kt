// NEW_NAME: existing1
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun existing1(): Int = 2 // DECL: 2
    }
}

fun Example.<caret>original9(): Int = 10 // DECL: 1

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.existing1() // REF: 2
