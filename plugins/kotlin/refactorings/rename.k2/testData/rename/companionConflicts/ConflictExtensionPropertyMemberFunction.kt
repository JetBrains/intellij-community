// NEW_NAME: existing7
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing7' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun existing7(): Int = 8
}

val Example.<caret>original8: () -> Int get() = { 9 }

fun useOriginal(): Int = Example().original8()

fun useExisting(): Int = Example().existing7()
