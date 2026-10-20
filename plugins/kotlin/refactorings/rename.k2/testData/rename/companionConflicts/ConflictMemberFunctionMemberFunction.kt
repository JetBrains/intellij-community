// NEW_NAME: existing7
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing7' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    fun <caret>original7(): Int = 8
    fun existing7(): Int = 8
}

fun useOriginal(): Int = Example().original7()
fun useExisting(): Int = Example().existing7()
