// API_VERSION: 1.8

class A {
    volatile int field1 = 0;

    private volatile String field2 = "";

    private volatile String neverWritten = "";

    A() {
        field2 = "new";
    }
}