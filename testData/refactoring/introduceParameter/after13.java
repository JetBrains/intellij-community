class T1 {
    int method(int i) {
        return 0;
    }
}

class T2 extends T1 {
    int method(int i, int anObject) {
        return anObject;
    }
}

class Usage {
    int m() {
        T2 test;
        return test.method(0, test.method(0) + 1);
    }
}