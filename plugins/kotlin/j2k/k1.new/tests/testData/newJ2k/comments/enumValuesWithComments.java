enum MyEnum {
    A,
    B;
}

class EnumTest {
    void saveFormatting() {
        MyEnum x = MyEnum.values()[1]; // First comment
        int y = MyEnum.values().length; // Second comment
        MyEnum[] z = MyEnum.values();  // Third comment
    }
}