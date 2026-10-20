// NEW_NAME: existing5
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing5' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {

}

companion fun Example.<caret>original5(): Int = 4

companion fun Example.existing5(): Int = 4

fun useOriginal(): Int = Example.original5()
fun useExisting(): Int = Example.existing5()
