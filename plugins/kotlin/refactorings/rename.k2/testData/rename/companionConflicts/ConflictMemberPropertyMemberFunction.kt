// NEW_NAME: existing7
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing7' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    val <caret>original6: () -> Int = { 7 }
    fun existing7(): Int = 8
}

fun useOriginal(): Int = Example().original6()
fun useExisting(): Int = Example().existing7()
