// NEW_NAME: existing5
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun <caret>original7(): Int = 8 // DECL: 1
}

companion fun Example.existing5(): Int = 4 // DECL: 2

fun useOriginal(): Int = Example().original7() // REF: 1
fun useExisting(): Int = Example.existing5() // REF: 2
