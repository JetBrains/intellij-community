class A {
    private Integer i = returnByte();

    static byte returnByte() { return 0; }

    void foo() {
        i = 10;
    }
}