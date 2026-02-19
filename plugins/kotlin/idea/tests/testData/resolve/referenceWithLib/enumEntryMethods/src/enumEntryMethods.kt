package test

import dependency.*

fun foo(libraryEnum: LibraryEnum, libraryJavaEnum: LibraryJavaEnum) {
    libraryEnum.<caret>name
    libraryEnum.<caret>ordinal
    libraryEnum.<caret>compareTo(libraryEnum)
    libraryEnum.<caret>equals(libraryEnum)
    libraryEnum.<caret>hashCode()

    libraryJavaEnum.<caret>name
    libraryJavaEnum.<caret>ordinal
    libraryJavaEnum.<caret>compareTo(libraryJavaEnum)
    libraryJavaEnum.<caret>equals(libraryJavaEnum)
    libraryJavaEnum.<caret>hashCode()
}

// REF1: (in kotlin.Enum).name
// REF2: (in kotlin.Enum).ordinal
// REF3: (in kotlin.Enum).compareTo(E)
// REF4: (in kotlin.Enum).equals(Any?)
// REF5: (in kotlin.Enum).hashCode()

// REF6: (in kotlin.Enum).name
// REF7: (in kotlin.Enum).ordinal
// REF8: (in kotlin.Enum).compareTo(E)
// REF9: (in kotlin.Enum).equals(Any?)
// REF10: (in kotlin.Enum).hashCode()
