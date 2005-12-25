class InvertBooleanParameterTest {
    void foo(boolean <caret>b) {
        boolean c = !b;
        b = false;
    }

    {
        foo(true);
    }
}

class Drv extends InvertBooleanParameterTest {
    void foo(boolean b) {
        super.foo(b);
    }
}