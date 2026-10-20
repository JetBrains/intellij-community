// NEW_NAME: existing3
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing3' is already declared in companion object 'Companion'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun <caret>original3(): Int = 6
        fun existing3(): Int = 6
    }
}

fun useOriginal(): Int = Example.original3()
fun useExisting(): Int = Example.existing3()
