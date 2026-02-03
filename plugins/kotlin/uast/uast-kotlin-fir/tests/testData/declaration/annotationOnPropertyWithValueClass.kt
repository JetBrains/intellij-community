package test.pkg

annotation class Anno

@JvmInline
value class IntValue(val value: Int) {
    companion object {
        @Anno val withValueClassTypeSpecified: IntValue = IntValue(0)
        @Anno val withValueClassTypeUnspecified = IntValue(1)
        @Anno val withNonValueClassTypeSpecified: Int = 2
        @Anno val withNonValueClassTypeUnSpecified = 3
    }
}
