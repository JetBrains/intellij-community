class C {
    void foo(Object o) {
        if (!(0 < 1)) return;
        System.out.println("Foo");
    }

    void bar(Object o) {
        if (!(0 <= 1)) return;
        System.out.println("Bar");
    }
}