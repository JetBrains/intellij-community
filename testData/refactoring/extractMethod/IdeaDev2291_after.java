class Tester {
    void method(Object... array) {
        Object object = null;
        newMethod(object, array);
    }

    private void newMethod(Object object, Object[] array) {
        array.equals(object);
    }
}