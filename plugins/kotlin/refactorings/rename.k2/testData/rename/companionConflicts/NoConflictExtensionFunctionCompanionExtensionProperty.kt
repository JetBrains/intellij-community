// NEW_NAME: existing2
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

fun Example.<caret>original9(): Int = 10 // DECL: 1

companion val Example.existing2: () -> Int get() = { 3 } // DECL: 2

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.existing2() // REF: 2
