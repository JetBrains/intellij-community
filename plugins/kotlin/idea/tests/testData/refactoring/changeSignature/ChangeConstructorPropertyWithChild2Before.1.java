import org.jetbrains.annotations.NotNull;

class JA extends A {
    public JA(@NotNull String a) {
        super(a);
    }

    @NotNull
    @Override
    public String getA() {
        return super.getA();
    }

    @Override
    public void setA(@NotNull String a) {
        super.setA(a);
    }
}

class Test {
    static void test() {
        new A("").getA();
        new A("").setA("awd");

        new B().getA();
        new B().setA("awd");

        new C("").getA();
        new C("").setA("awd");

        new JA("").getA();
        new JA("").setA("awd");
    }
}
