class A<caret> {
    private A() {
    }

    static A newA() {
        return new A();
    }
}

public class B {
    A a = A.newA();
}