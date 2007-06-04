class A {
    private Inner[] b = new Inner[0];

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}