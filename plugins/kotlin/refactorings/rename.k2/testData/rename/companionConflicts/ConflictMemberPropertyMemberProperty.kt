// NEW_NAME: existing6
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing6' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    val <caret>original6: () -> Int = { 7 }
    val existing6: () -> Int = { 7 }
}

fun useOriginal(): Int = Example().original6()
fun useExisting(): Int = Example().existing6()
