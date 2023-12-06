interface I {
    int getSomething();
    void setSomething(String value);
}

class C implements I {

    @Override
    public int getSomething() {
        return 0;
    }

    @Override
    public void setSomething(String value) {
        System.out.println("set");
    }

    public static void test(I i) {
        System.out.println(i.getSomething());
        i.setSomething("new");
    }
}