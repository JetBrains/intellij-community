@interface Value {
    String value();
}

class Outer {
    enum NestedEnum {
        @Value("NV1")
        NValue1,
        @Value("NV2")
        NValue2,
    }
}

enum TopLevelEnum {
    @Value("TV1")
    TValue1,
    @Value("TV2")
    TValue2,
}