// NEW_NAME: existing3
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing3' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        val <caret>original0: () -> Int = { 1 }
    }
}

companion fun Example.existing3(): Int = 4

fun useOriginal(): Int = Example.original0()
fun useExisting(): Int = Example.existing3()
