import library.java.test.LibraryEnum

internal class EnumTest {
    fun libraryTest() {
        val x = LibraryEnum.entries[1]
        val y = LibraryEnum.entries.toTypedArray()
    }
}
