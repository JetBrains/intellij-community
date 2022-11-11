// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries

class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    void testToReplaceEnumValues() {
        MyEnum x1 = MyEnum.values()[1];
        MyEnumKt x2 = MyEnumKt.values()[1];
        MyEnum[] y1 = MyEnum.values();
        MyEnumKt[] y2 = MyEnumKt.values();
    }
}