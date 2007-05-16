class Test {
    int m(int anObject) {
        return anObject;
    }
}

class X3 {
    int n() {
        Test t;
        return t.m(0);
    }
}