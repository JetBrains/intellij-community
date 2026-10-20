// NEW_NAME: existing2
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        val existing2: () -> Int = { 5 } // DECL: 2
    }
}

companion fun Example.<caret>original5(): Int = 4 // DECL: 1

fun useOriginal(): Int = Example.original5() // REF: 1
fun useExisting(): Int = Example.Companion.existing2() // REF: 2
