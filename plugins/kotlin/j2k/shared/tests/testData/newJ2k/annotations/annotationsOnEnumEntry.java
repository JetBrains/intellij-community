import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@interface Ann1 {
}

@Target({ElementType.FIELD})
@interface Ann2 {
}

enum Foo {
    @Ann1 @Ann2 A,
    @Ann1 B, @Ann1 C
}

@interface Value {
    String value();
}

class Outer {
    enum NestedEnum {
        @Value("NV1")
        NValue1,
        @Value("NV2") NValue2,
    }
}

enum TopLevelEnum {
    @Value("TV1")
    TValue1,
    @Value("TV2") TValue2,
}