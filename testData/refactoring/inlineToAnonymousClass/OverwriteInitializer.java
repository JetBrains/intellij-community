class A {
    private Object b = new Inner(100);

    private class <caret>Inner {
        private int myInt = 0;

        public Inner() {
        }

        public Inner(int anInt) {
	    myInt = anInt;
        }

        public int getInt() {
	    return myInt;
        }
    }
}