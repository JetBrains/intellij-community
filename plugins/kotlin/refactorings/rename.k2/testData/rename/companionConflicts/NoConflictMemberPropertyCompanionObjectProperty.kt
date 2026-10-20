// NEW_NAME: existing2
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    val <caret>original6: () -> Int = { 7 } // DECL: 1

    companion object {
        val existing2: () -> Int = { 5 } // DECL: 2
    }
}

fun useOriginal(): Int = Example().original6() // REF: 1
fun useExisting(): Int = Example.existing2() // REF: 2
