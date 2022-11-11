// COMPILER_ARGUMENTS: -XXLanguage:+EnumEntries

enum MyEnum {
    A,
    B;
}

class EnumTest {
    //TODO: Remove after Enum.entries is marked as non-experimental in Kotlin 1.9
    @ExperimentalStdlibApi
    void saveFormatting() {
        MyEnum x = MyEnum.values()[1]; // First comment
        int y = MyEnum.values().length; // Second comment
        MyEnum[] z = MyEnum.values();  // Third comment
    }
}