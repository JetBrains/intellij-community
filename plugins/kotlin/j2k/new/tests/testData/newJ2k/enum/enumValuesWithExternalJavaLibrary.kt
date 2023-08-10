import library.java.test.LibraryEnum

internal class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    fun libraryTest() {
        val x = LibraryEnum.entries[1]
        val y = LibraryEnum.entries.toTypedArray()
    }
}
