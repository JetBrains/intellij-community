// NEW_NAME: existing0
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun <caret>original7(): Int = 8 // DECL: 1

    companion {
        val existing0: () -> Int = { 1 } // DECL: 2
    }
}

fun useOriginal(): Int = Example().original7() // REF: 1
fun useExisting(): Int = Example.existing0() // REF: 2
