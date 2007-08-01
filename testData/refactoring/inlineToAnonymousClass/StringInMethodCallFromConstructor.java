class <caret>Inlined {
    private int myA;

    public Inlined(String s) {
        process(s);
    }

    public void process(String s) {
        myA = s.length();
    }
}

class C {
    public void test() {
        Inlined i = new Inlined("a");
    }
}