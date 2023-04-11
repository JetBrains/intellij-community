package test

import dependency.*

fun foo() {
    val ldc = LibraryDataClass("str")
    ldc.<caret>component1()
    ldc.<caret>copy()
    ldc.<caret>equals(ldc)
    ldc.<caret>hashCode()
    ldc.<caret>toString()
}

// REF1: (in dependency.LibraryDataClass).component1()
// REF2: (dependency).LibraryDataClass
// REF3: (dependency).LibraryDataClass
// REF4: (dependency).LibraryDataClass
// REF5: (dependency).LibraryDataClass
