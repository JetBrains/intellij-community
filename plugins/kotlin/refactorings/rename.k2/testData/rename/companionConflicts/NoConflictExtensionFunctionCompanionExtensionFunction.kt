// NEW_NAME: existing3
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

fun Example.<caret>original9(): Int = 10 // DECL: 1

companion fun Example.existing3(): Int = 4 // DECL: 2

fun useOriginal(): Int = Example().original9() // REF: 1

fun useExisting(): Int = Example.existing3() // REF: 2
