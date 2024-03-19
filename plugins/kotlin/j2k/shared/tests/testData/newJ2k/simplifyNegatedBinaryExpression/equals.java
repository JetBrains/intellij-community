class C {
    void foo(Object o) {
        if (!(0 != 1)) return;
        System.out.println("String");
    }

    public boolean bar() {
        return 1 == 2 == !(3 == 4);
    }
}