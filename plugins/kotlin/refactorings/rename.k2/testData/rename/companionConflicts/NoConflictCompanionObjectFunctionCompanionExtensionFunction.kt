// NEW_NAME: existing5
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun <caret>original3(): Int = 6 // DECL: 1
    }
}

companion fun Example.existing5(): Int = 4 // DECL: 2

fun useOriginal(): Int = Example.Companion.original3() // REF: 1
fun useExisting(): Int = Example.existing5() // REF: 2
