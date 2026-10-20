// NEW_NAME: existing8
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing8' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

val Example.<caret>original8: () -> Int get() = { 9 }

val Example.existing8: () -> Int get() = { 9 }

fun useOriginal(): Int = Example().original8()

fun useExisting(): Int = Example().existing8()
