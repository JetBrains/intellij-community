package test

import dependency.*

@kotlin.ExperimentalStdlibApi
fun foo() {
    LibraryEnum.<caret>values()
    LibraryEnum.<caret>valueOf("ONE")
    LibraryEnum.<caret>entries
    LibraryJavaEnum.<caret>values()
    LibraryJavaEnum.<caret>valueOf("ONE")
    LibraryJavaEnum.<caret>entries
}

// ALLOW_AST_ACCESS

// REF1: (dependency).LibraryEnum
// REF2: (dependency).LibraryEnum
// REF3: (dependency).LibraryEnum
// REF4: (dependency).LibraryJavaEnum
// REF5: (dependency).LibraryJavaEnum
// REF6: (dependency).LibraryJavaEnum
