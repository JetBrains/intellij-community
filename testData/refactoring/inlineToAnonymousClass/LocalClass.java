class A {
    public void test() {
        class <caret>Inner {
            public String toString() {
                return "A";
            }
        }

        Object b = new Inner();
    }
}