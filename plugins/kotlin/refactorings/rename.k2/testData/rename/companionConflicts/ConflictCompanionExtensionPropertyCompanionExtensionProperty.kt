// NEW_NAME: existing4
// RENAME: member
// SHOULD_FAIL_WITH: Property 'existing4' is already declared in package 'test'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {

}

companion val Example.<caret>original4: () -> Int get() = { 3 }

companion val Example.existing4: () -> Int get() = { 3 }

fun useOriginal(): Int = Example.original4()
fun useExisting(): Int = Example.existing4()
