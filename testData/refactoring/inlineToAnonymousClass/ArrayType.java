class A {
    public void test() {
        Inner[] b = new Inner[1];
        b [0] = new Inner();
    }

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}