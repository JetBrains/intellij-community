// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
internal enum class MyEnum {
    A,
    B
}

internal class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    fun saveFormatting() {
        val x = MyEnum.entries[1] // First comment
        val y = MyEnum.entries.size // Second comment
        val z = MyEnum.entries.toTypedArray() // Third comment
    }
}
