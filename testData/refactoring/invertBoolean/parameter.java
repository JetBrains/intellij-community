class InvertBooleanParameterTest {
    void foo(boolean <caret>b) {
        boolean c = !b;
    }

    {
        foo(true);
    }
}