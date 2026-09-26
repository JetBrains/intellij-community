class Outer {
    void test(C c) {
        System.out.println(c.myString);
        System.out.println(c.getString());
    }

    private static class C {
        private String myString = "";

        public void report(String s) {
            myString = s;
        }

        public String getString() {
            return myString;
        }

        void test() {
            System.out.println(myString);
            System.out.println(getString());
        }
    }
}