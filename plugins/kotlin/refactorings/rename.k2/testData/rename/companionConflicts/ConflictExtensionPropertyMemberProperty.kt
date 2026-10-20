// NEW_NAME: existing6
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing6' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    val existing6: () -> Int = { 7 }
}

val Example.<caret>original8: () -> Int get() = { 9 }

fun useOriginal(): Int = Example().original8()

fun useExisting(): Int = Example().existing6()
