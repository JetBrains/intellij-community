public class Y {
    public void foo() {
    }
}
public class X {
    static void <caret>method(Y y) {
        System.out.println(y);
        y.foo();
    }

    {
      Y y = new Y();
      method(y);
      method(new Y());
    }
}