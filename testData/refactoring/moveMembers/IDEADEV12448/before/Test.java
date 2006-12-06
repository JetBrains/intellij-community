@interface SomeAnnotation {
    String value();
}

@SomeAnnotation(B.CONST)
interface A {}

interface B { String CONST = "42"; }