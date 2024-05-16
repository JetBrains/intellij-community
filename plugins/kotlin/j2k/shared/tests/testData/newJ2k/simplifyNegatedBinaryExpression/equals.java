class C {
    void foo(int a, int b, String s1, String s2) {
        if (!(0 != 1)) return;
        if (!(0 != 1) && a > b) return;
        if (0 == 1 && !/*comment 1*/(/*comment 2*/a == b)) return;

        if (!(s1 == s2)) return;
        if (!(s1 != s2)) return;
    }

    public boolean bar() {
        return 1 == 2 == !(3 == 4);
    }
}