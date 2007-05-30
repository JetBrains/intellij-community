class A {
    private Object b = new Inner();

    private class <caret>Inner {
        private int i=0;

        public String toString() {
            i++;
            return "A";
        }
    }
}