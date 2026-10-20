// NEW_NAME: existing3
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

val Example.<caret>original8: () -> Int get() = { 9 } // DECL: 1

companion fun Example.existing3(): Int = 4 // DECL: 2

fun useOriginal(): Int = Example().original8() // REF: 1

fun useExisting(): Int = Example.existing3() // REF: 2
