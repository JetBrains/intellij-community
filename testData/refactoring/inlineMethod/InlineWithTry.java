class A {
    {
        g();
    }
    int <caret>g() {
        try {
            return 0;
        } catch (Error e) {
            throw e;
        }
    }
}
