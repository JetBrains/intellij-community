class A {
    void foo() {
        char charDigit = '4';
        int radix = 10;
        int intDigit = Character.digit(charDigit, radix);
        int literalRadix = Character.digit(charDigit, 10);
    }
}