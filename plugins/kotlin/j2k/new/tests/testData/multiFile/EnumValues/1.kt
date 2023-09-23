internal class EnumTest {
    fun testToReplaceEnumValues() {
        val x1 = MyEnum.entries[1]
        val x2 = MyEnumKt.entries[1]
        val y1 = MyEnum.entries.toTypedArray()
        val y2 = MyEnumKt.entries.toTypedArray()
    }
}
