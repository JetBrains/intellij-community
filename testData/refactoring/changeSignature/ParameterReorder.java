class A {
    public int method<caret>(int i, int j) {
        return i - j;
    }
}

class B extends A {
    public void method(int j, int i) {
        return i - j;
    }
}