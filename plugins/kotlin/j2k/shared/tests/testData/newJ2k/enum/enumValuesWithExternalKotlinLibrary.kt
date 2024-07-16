import library.kotlin.test.LibraryEnumKt

internal class EnumTest {
    fun libraryTest() {
        val x = LibraryEnumKt.entries[1]
        val y = LibraryEnumKt.entries.toTypedArray()
    }
}
