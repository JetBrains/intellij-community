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
}

class Test {
    static void test() {
        new A("").getB();

        new B().getB();

        new C("").getB();

        new JA("").getB();
    }
}
