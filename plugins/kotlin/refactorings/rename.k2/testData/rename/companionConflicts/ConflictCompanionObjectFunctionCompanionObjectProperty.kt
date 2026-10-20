// NEW_NAME: existing2
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing2' is already declared in companion object 'Companion'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion object {
        fun <caret>original3(): Int = 6
        val existing2: () -> Int = { 5 }
    }
}

fun useOriginal(): Int = Example.original3()
fun useExisting(): Int = Example.existing2()
