class A {
    private Inner b = new Inner();

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}