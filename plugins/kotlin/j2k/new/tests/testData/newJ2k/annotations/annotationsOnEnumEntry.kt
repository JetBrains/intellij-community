@Target(AnnotationTarget.FIELD)
internal annotation class Ann1

@Target(AnnotationTarget.FIELD)
internal annotation class Ann2

internal enum class Foo {
    @Ann1
    @Ann2
    A,

    @Ann1
    B,

    @Ann1
    C
}

internal annotation class Value(val value: String)

internal class Outer {
    internal enum class NestedEnum {
        @Value("NV1")
        NValue1,

        @Value("NV2")
        NValue2,
    }
}

internal enum class TopLevelEnum {
    @Value("TV1")
    TValue1,

    @Value("TV2")
    TValue2,
}
