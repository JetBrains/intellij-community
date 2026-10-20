// NEW_NAME: existing8
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

companion val Example.<caret>original2: () -> Int get() = { 3 } // DECL: 1

val Example.existing8: () -> Int get() = { 9 } // DECL: 2

fun useOriginal(): Int = Example.original2() // REF: 1

fun useExisting(): Int = Example().existing8() // REF: 2
