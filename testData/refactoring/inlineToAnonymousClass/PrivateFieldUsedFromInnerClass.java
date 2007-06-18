class A {
    class <caret>Inner {
        private int myNonStaticField = 0;

        class InnerNonStatic {
            private int myInsField = 0;

            public void insMethod() {
                myNonStaticField += myInsField;
            }
        }
    }
}

class B {
    public void test() {
        A.Inner inner = new A.Inner();
    }
}