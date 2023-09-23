class EnumTest {
    void testToReplaceEnumValues() {
        MyEnum x1 = MyEnum.values()[1];
        MyEnumKt x2 = MyEnumKt.values()[1];
        MyEnum[] y1 = MyEnum.values();
        MyEnumKt[] y2 = MyEnumKt.values();
    }
}