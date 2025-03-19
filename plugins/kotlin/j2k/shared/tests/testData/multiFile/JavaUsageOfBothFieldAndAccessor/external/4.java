package test;

public class Test {
    void bar(J j) {
        System.out.println(j.foo);
        j.foo = 43;
        System.out.println(j.getFoo());
        j.setFoo(43);
    }
}