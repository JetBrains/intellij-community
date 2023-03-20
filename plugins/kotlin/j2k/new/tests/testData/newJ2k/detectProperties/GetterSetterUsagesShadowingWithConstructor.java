public class A {
    private String foo;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public void A(String foo) {
        setFoo(foo);
        System.out.println(getFoo());
    }
}