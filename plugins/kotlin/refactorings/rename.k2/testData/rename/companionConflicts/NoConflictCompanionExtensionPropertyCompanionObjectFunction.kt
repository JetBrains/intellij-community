// NEW_NAME: existing3
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun existing3(): Int = 6 // DECL: 2
    }
}

companion val Example.<caret>original4: () -> Int get() = { 3 } // DECL: 1

fun useOriginal(): Int = Example.original4() // REF: 1
fun useExisting(): Int = Example.Companion.existing3() // REF: 2
