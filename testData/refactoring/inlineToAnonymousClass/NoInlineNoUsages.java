class A {
    private class <caret>Inner {
        public static Inner newInstance() {
            return new Inner();
        }
    }
}