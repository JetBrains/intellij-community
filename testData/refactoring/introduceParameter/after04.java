class Test {
    int method(int a, int b, int anObject) {
        return anObject;
    }
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2, 1 + 2);
    }
}