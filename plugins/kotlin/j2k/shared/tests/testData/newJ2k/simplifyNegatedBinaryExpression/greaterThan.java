class C {
    void foo() {
        if (!(0 > 1)) return;
        System.out.println("Foo");
    }

    void bar() {
        if (!(0 >= 1)) return;
        System.out.println("Bar");
    }
}