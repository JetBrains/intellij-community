import org.jetbrains.annotations.NotNull;

class JA extends A {
    public JA(@NotNull String a) {
        super(a);
    }

    @NotNull
    @Override
    public int getB() {
        return super.getB();
    }

    @Override
    public void setB(@NotNull int a) {
        super.setB(a);
    }
}

class Test {
    static void test() {
        new A("").getB();
        new A("").setB("awd");

        new B().getB();
        new B().setB("awd");

        new C("").getB();
        new C("").setB("awd");

        new JA("").getB();
        new JA("").setB("awd");
    }
}
