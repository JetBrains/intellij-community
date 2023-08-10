// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries
internal class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    fun testToReplaceEnumValues() {
        val x1 = MyEnum.entries[1]
        val x2 = MyEnumKt.entries[1]
        val y1 = MyEnum.entries.toTypedArray()
        val y2 = MyEnumKt.entries.toTypedArray()
    }
}
