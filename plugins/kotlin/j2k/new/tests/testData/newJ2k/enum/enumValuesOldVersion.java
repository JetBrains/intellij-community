// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries

enum MyEnum {
    A,
    B;
}

class EnumTest {
    void test() {
        MyEnum x = MyEnum.values()[1];
    }
}