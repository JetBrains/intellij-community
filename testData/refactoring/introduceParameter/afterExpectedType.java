class Test {
    void f (String s) {}

    void u(final String anObject) {
        f(anObject);
    }

    void y () {
        String name = "";
        u(name);
    }
}
