// NEW_NAME: existing9
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing9' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

fun Example.<caret>original9(): Int = 10

fun Example.existing9(): Int = 10

fun useOriginal(): Int = Example().original9()

fun useExisting(): Int = Example().existing9()
