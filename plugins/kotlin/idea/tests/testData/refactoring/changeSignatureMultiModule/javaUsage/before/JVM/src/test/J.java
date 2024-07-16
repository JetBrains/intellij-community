package test;
class J {
    public static void main(String[] args) {
        new B().m();
        new C().m();
    }
}

class B extends C {
    public void m() {}
}