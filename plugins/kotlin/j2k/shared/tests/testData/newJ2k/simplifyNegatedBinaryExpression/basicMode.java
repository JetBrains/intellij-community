// !BASIC_MODE: true
class C {
    void foo(int a, int b, String s1, String s2) {
        if (!(0 != 1)) return;
        if (!(s1 == s2)) return;
        if (!(a > b)) return;
    }
}