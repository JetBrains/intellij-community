// WITH_LIBRARY: ../../libSources/enum/java

import library.java.test.LibraryEnum;

class EnumTest {
    void libraryTest() {
        LibraryEnum x = LibraryEnum.values()[1];
        LibraryEnum[] y = LibraryEnum.values();
    }
}