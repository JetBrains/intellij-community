public class Test {
    public static void test() {
        FooImpl foo = new FooImpl();
        System.out.println(foo.getA());
        foo.setA(42);
    }
}