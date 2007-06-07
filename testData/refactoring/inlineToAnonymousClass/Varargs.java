class A {
    public void test() {
        Object o = new Inner(1, 2, 3);
    }

    private class <caret>Inner {
        private int length;

        public Inner(int... values) {
            length = values.length;
        }

        public String toString() {
            return Integer.toString(length);
        }
    }
}