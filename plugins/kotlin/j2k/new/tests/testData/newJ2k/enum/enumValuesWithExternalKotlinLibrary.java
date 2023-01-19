// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// WITH_LIBRARY: /enum/kotlin
// !LANGUAGE: -XXLanguage:-EnumEntries

import library.kotlin.test.LibraryEnumKt;

class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    void libraryTest() {
        LibraryEnumKt x = LibraryEnumKt.values()[1];
        LibraryEnumKt[] y = LibraryEnumKt.values();
    }
}