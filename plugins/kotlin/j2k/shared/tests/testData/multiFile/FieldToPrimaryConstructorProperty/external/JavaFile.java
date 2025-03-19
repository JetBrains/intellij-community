package test;

public class JavaFile {
    void foo(JJ jj) {
        System.out.println(jj.x);
        System.out.println(jj.getX());
        jj.x = 42;
        jj.setX(42);
    }
}