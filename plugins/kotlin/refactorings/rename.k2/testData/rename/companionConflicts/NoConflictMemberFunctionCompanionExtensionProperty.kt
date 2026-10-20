// NEW_NAME: existing4
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun <caret>original7(): Int = 8 // DECL: 1
}

companion val Example.existing4: () -> Int get() = { 3 } // DECL: 2

fun useOriginal(): Int = Example().original7() // REF: 1
fun useExisting(): Int = Example.existing4() // REF: 2
