// NEW_NAME: existing1
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing1' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun <caret>original1(): Int = 2
        fun existing1(): Int = 2
    }
}

fun useOriginal(): Int = Example.original1()
fun useExisting(): Int = Example.existing1()
