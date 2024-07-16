// COMPILER_ARGUMENTS: -XXLanguage:-EnumEntries
internal enum class MyEnum {
    A,
    B
}

internal class EnumTest {
    fun test() {
        val x = MyEnum.values()[1]
    }
}
