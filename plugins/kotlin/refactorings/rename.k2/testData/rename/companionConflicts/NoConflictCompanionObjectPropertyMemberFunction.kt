// NEW_NAME: existing7
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun existing7(): Int = 8 // DECL: 2

    companion object {
        val <caret>original2: () -> Int = { 5 } // DECL: 1
    }
}

fun useOriginal(): Int = Example.original2() // REF: 1
fun useExisting(): Int = Example().existing7() // REF: 2
