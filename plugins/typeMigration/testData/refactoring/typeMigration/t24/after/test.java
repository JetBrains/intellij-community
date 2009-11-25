public class Test {
    D f;
    C foo() {
       return null;
    }
}
class B extends Test {
    C foo() {
        return f;
    }
}

class C {}
class D extends C{}
