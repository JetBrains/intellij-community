// Items: super, this, class, new, var
public class Foo {
    void m() {
        Foo foo = new Foo();<caret>
    }
}