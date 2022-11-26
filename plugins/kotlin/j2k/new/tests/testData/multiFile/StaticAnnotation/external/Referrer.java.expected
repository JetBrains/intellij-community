import java.util.List;

public class Referrer {
    public void ref1() {
        ConvertMe1.foo("A", "B");
        ConvertMe1.foo("A");
        ConvertMe1.foo(1);
    }

    public void ref2() {
        ConvertMe2.foo("A", 2);
    }

    public void ref3() {
        ConvertMe3.foo(2, "A");
    }

    public void ref4() {
        ConvertMe4.foo("A", 2);
        ConvertMe4.foo(2, "A");
    }

    public void ref5() {
        ConvertMe5.foo(1);
    }

    public void ref6() {
        SimpleClass simpleClass = new SimpleClass();
        ConvertMe6.foo(simpleClass);
    }
}