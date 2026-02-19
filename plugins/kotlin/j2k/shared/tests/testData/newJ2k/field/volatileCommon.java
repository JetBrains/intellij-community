// API_VERSION: 1.9
// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi

class A {
    volatile int field1 = 0;

    private volatile String field2 = "";

    A() {
        field2 = "new";
    }
}