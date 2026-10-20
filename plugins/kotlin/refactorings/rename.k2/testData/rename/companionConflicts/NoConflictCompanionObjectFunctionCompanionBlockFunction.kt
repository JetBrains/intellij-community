// NEW_NAME: existing1
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun <caret>original3(): Int = 6 // DECL: 1
    }

    companion {
        fun existing1(): Int = 2 // DECL: 2
    }
}

fun useOriginal(): Int = Example.Companion.original3() // REF: 1
fun useExisting(): Int = Example.existing1() // REF: 2
