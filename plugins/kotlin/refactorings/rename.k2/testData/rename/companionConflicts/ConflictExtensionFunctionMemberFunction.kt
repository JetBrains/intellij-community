// NEW_NAME: existing7
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing7' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun existing7(): Int = 8
}

fun Example.<caret>original9(): Int = 10

fun useOriginal(): Int = Example().original9()

fun useExisting(): Int = Example().existing7()
