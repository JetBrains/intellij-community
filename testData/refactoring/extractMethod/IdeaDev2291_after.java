class Tester {
    void method(Object... array) {
        Object object = null;
        newMethod(array.equals(object));
    }

    private void newMethod(boolean b) {
        b;
    }
}