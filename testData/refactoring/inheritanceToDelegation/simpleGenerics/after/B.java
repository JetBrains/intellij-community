class B {
    public final A<String> myDelegate = new A<String>();

    public List<? extends String> method1() {
        return myDelegate.method1();
    }

    public String method2(String s) {
        return myDelegate.method2(s);
    }
}