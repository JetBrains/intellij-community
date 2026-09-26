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
}

class Test {
    static void test() {
        new A("").getA();

        new B().getA();

        new C("").getA();

        new JA("").getA();
    }
}
