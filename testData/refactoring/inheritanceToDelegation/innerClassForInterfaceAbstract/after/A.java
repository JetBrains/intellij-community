public abstract class A extends BaseClass {
    private final MyBaseInterface myBaseInterface = new MyBaseInterface();

    public A() {
    }

    public A(String s) {
        super(27);
    }

    public abstract void baseInterfaceMethod();

    private class MyBaseInterface implements BaseInterface {
        public void baseInterfaceMethod() {
            A.this.baseInterfaceMethod();
        }
    }
}