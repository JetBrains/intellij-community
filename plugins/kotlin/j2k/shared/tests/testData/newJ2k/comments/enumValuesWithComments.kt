internal enum class MyEnum {
    A,
    B
}

internal class EnumTest {
    fun saveFormatting() {
        val x = MyEnum.entries[1] // First comment
        val y = MyEnum.entries.size // Second comment
        val z = MyEnum.entries.toTypedArray() // Third comment
    }
}
