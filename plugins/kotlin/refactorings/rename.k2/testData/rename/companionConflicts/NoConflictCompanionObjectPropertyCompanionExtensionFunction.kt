// NEW_NAME: existing5
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        val <caret>original2: () -> Int = { 5 } // DECL: 1
    }
}

companion fun Example.existing5(): Int = 4 // DECL: 2

fun useOriginal(): Int = Example.Companion.original2() // REF: 1
fun useExisting(): Int = Example.existing5() // REF: 2
