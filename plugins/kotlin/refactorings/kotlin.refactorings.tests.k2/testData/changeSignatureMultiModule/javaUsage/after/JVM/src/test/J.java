package test;
class J {
    public static void main(String[] args) {
        new B().m(false);
        new C().m(false);
    }
}

class B extends C {
    public void m(boolean b) {}
}