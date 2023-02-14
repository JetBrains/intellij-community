// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
// WITH_LIBRARY: /enum/java
// !LANGUAGE: -XXLanguage:+EnumEntries

import library.java.test.LibraryEnum;

class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    void libraryTest() {
        LibraryEnum x = LibraryEnum.values()[1];
        LibraryEnum[] y = LibraryEnum.values();
    }
}