class B {
    public final A myDelegate = new A();

    public void methodFromA() {
        myDelegate.methodFromA();
    }

    public int intMethodFromA(int i) {
        return myDelegate.intMethodFromA(i);
    }
}