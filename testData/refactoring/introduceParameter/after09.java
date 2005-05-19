class Test {
    public static int i;
    
    int method(int a, int anObject) {
        return anObject;
    }
}

class X {
    public static int i;
    
    int yyy(int z) {
        Test t;
        return t.method(z, z + Test.i);
    }
}