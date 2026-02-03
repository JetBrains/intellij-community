enum class TestEnum {
    A,
    B;

    companion object {
        fun parse(): TestEnum {
            return TestEnum.A
        }
    }
}

internal class Go {
    fun fn() {
        val x: TestEnum = TestEnum.Companion.parse()
    }
}
