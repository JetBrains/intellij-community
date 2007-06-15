class Outer {
    int i;

    class Inner {
        public void f() {
            int j = i;
        }
    }

    void foo() {
        final int i = 0;
        new <caret>Inner(); 
    }
}