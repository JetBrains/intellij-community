class A {
    public void test() {
        try {
            test2();
        }
        catch(Exception ex) {
        }
    }

    private void test2() throws Inner {
        throw new Inner();
    }

    private class <caret>Inner extends Exception {
        public String toString() {
            return "A";
        }
    }
}