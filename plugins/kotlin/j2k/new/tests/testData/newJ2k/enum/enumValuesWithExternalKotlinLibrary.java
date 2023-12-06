// WITH_LIBRARY: ../../libSources/enum/kotlin
// !LANGUAGE: -XXLanguage:-EnumEntries

import library.kotlin.test.LibraryEnumKt;

class EnumTest {
    void libraryTest() {
        LibraryEnumKt x = LibraryEnumKt.values()[1];
        LibraryEnumKt[] y = LibraryEnumKt.values();
    }
}