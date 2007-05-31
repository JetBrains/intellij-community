class A {
    private Object b = new Inner();

    private class <caret>Inner {
        public Inner() {
            doStuff();
        }

        public String doStuff() {
            return "A";
        }
    }
}