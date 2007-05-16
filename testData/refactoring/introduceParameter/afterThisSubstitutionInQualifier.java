class Test {
    int method(int a, int b, int anObject) {
        return anObject;
    }
    private int i;
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2, t.i);
    }
}