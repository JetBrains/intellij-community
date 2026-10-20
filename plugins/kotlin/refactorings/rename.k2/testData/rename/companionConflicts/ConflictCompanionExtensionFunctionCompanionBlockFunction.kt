// NEW_NAME: existing1
// RENAME: member
// SHOULD_FAIL_WITH: Function 'existing1' is already declared in class 'Example'
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example {
    companion {
        fun existing1(): Int = 2
    }
}

companion fun Example.<caret>original3(): Int = 4

fun useOriginal(): Int = Example.original3()
fun useExisting(): Int = Example.existing1()
