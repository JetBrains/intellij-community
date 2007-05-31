import java.io;

class A {
    public void test() {
        Inlined i = new Inlined();
    }
}

class <caret>Inlined {
    public class A {
    }

    private A myA = new A();

    public String toString() {
        A a = new A();
        return a.toString();
    }
}
