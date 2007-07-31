public class A implements Intf, Intf2 {
    public void method1() {
        method2();
    }

    public void method2 () {
        System.out.println("2");
    }
}