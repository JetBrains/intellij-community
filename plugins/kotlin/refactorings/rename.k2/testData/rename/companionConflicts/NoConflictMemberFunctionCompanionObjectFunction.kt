// NEW_NAME: existing3
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun <caret>original7(): Int = 8 // DECL: 1

    companion object {
        fun existing3(): Int = 6 // DECL: 2
    }
}

fun useOriginal(): Int = Example().original7() // REF: 1
fun useExisting(): Int = Example.existing3() // REF: 2
