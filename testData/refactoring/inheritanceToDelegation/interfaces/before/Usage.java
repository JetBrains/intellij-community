class Usage {
    private A myA = new A();
    public void methodExpectingI(I i) {
        i.methodFromI();
    }

    public J methodReturningJ() {
        return myA;
    }

    public void methodExpectingJ(J j) {
        j.methodFromJ();
    }

    public void main() {
        A a = new A();
        a.methodFromI();
        a.methodFromJ();
        methodExpectingI(a);
        methodExpectingJ(a);
        methodExpectingJ(myA);
    }
}