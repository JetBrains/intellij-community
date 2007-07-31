public class A extends ASuper {
    private final MyIntf myDelegate = new MyIntf();

    public Intf getMyDelegate() {
        return myDelegate;
    }

    private class MyIntf implements Intf {
        public void method1 () {
            System.out.println("1");
        }

        public void method2() {
            A.this.method2();
        }
    }
}