// NEW_NAME: existing3
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing3' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun <caret>original1(): Int = 2
    }
}

companion fun Example.existing3(): Int = 4

fun useOriginal(): Int = Example.original1()
fun useExisting(): Int = Example.existing3()
