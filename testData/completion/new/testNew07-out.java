// Items: class, new, var
public class Foo {
    void m() {
        new IFoo() {<caret>
        };
        f();
    }
}

interface IFoo {

}