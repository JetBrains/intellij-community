@interface Ann {}

@interface Foo {
    @Ann String value();
    @Ann String value2() default "test";
}