class OuterClass {
    class InnerClass {
        int i;
        InnerClass(int _i) {<caret>
            i = _i;
        }
    }
    InnerClass myInner = new InnerClass(27);
    static int method() {
        OuterClass test = new OuterClass();
        InnerClass inner = test.new InnerClass(15);
    }
}