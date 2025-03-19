class A {
    public A(double d) {}

    public void testPrimary(int i) {
        System.out.println(new A(1));
        System.out.println(new A(i));
        A a1 = new A(1);
        A a2 = new A(i);
    }
}

class B {
    public B(int i) {}
    public B(String s) {}
    public B(int i, String s) {}
    public B(int i, double d) {}

    public void testSecondary(int i) {
        System.out.println(new B(1, 1));
        System.out.println(new B(i, i));
        B b1 = new B(1, 1);
        B b2 = new B(i, i);
    }
}

class C {
    public C(double d) {}
    void foo(double d) {}

    public void testAnonymousClass(int i) {
        new Runnable() {
            @Override
            public void run() {
                System.out.println(new C(1));
                System.out.println(new C(i));
                foo(1);
                foo(i);
            }
        };
    }
}
