public class A {
    private String foo;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public boolean g1(String foo) {
        return getFoo().equals(foo);
    }

    public boolean g2(String foo) {
        return this.getFoo().equals(foo);
    }

    public boolean g3(String foo, A other) {
        return other.getFoo().equals(foo);
    }

    public boolean g4(String foo) {
        return getFoo().equalsIgnoreCase(foo);
    }

    public boolean g5(String foo) {
        return this.getFoo().equalsIgnoreCase(foo);
    }

    public boolean g6(String foo, A other) {
        return other.getFoo().equalsIgnoreCase(foo);
    }

    public void s1(String foo) {
        setFoo(foo);
    }

    public void s2(String foo) {
        this.setFoo(foo);
    }

    public void s3() {
        String foo = "";
        setFoo(foo);
    }
}