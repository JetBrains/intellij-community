// NEW_NAME: existing9
// RENAME: member
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
package test

class Example

companion fun Example.<caret>original3(): Int = 4 // DECL: 1

fun Example.existing9(): Int = 10 // DECL: 2

fun useOriginal(): Int = Example.original3() // REF: 1

fun useExisting(): Int = Example().existing9() // REF: 2
