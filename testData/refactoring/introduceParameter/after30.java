class Test {
    void f (String s) {}

    void u(final String aString) {
        f(aString);
    }

    void y () {
        String name = "";
        u(name);
    }
}
