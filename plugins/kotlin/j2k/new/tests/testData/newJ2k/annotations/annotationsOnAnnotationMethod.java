@interface NonNls {}

@interface Foo {
    @NonNls String value();
    @NonNls String value2() default "test";
}