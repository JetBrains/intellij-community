class A {
    public void f() {
        Inner i = new Inner();
        i.doStuff();
    }

    private class <caret>Inner {
        public void doStuff() {
        }
    }
}