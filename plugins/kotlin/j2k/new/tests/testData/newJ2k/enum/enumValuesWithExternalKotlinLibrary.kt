import library.kotlin.test.LibraryEnumKt

internal class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    fun libraryTest() {
        val x = LibraryEnumKt.entries[1]
        val y = LibraryEnumKt.entries.toTypedArray()
    }
}
