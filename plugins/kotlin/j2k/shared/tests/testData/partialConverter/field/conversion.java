class A {
    private Integer <caret>i = takeByte();

    static byte takeByte() { return 0; }

    void foo() {
        i = 10;
    }
}
