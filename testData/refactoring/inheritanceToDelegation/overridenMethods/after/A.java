class A {
    int fieldFromA;
    private final MyBase myDelegate = new MyBase();

    public void firstMethodFromBase() {
        myDelegate.firstMethodFromBase();
    }

    private class MyBase extends Base {
        public void firstMethodFromBase() {
            super.firstMethodFromBase();
        }

        public void secondMethodFromBase() {
            fieldFromA = 27;
            A.this.fieldFromA++;
        }

        Base getInstance() {
            return this;
        }
    }
}