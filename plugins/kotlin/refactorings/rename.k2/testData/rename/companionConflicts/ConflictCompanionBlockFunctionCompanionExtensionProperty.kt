// NEW_NAME: existing2
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing2' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun <caret>original1(): Int = 2
    }
}

companion val Example.existing2: () -> Int get() = { 3 }

fun useOriginal(): Int = Example.original1()
fun useExisting(): Int = Example.existing2()
