// NEW_NAME: existing0
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing0' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        val <caret>original0: () -> Int = { 1 }
        val existing0: () -> Int = { 1 }
    }
}

fun useOriginal(): Int = Example.original0()
fun useExisting(): Int = Example.existing0()
