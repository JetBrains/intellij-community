inline fun <T : Any> oneParam(block: (T) -> Unit) {}

fun testNormalIt() {
    oneParam { it }
}

enum class TestEnum {
    TE_1, TE_2
}

fun testEnumEntries() {
    TestEnum.entries.map { it }
}
