import org.jetbrains.annotations.Nullable;

@interface Foo {
    @Nullable String value();
    @Nullable String value2() default "test";
}