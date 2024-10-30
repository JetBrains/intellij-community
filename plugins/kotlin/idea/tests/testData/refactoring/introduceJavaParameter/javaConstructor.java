class A {
    int x;

    A(int x) {
        this.x = x;
    }
}

class J {
    J(int a, int b, int c) {
        int p = <selection>new A(a + b)</selection>.x * c
    }
}

class Test {
    void test() {
        new J(1, 2, 3);
    }
}
