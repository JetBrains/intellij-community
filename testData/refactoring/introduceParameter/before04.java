class Test {
    int method(int a, int b) {
        return <selection>a + b</selection>;
    }
}

class XTest {
    int n() {
        Test t;
        
        return t.method(1, 2);
    }
}