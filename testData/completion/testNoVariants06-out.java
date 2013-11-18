// Items: Boo.Boo2, Boo.Boo3, Boo.var, Boo2.var, Boo3.var
public class Boo {
    void m() {
        Boo foo = new Boo();<caret>
    }

    class Boo2 { }
    class Boo3 { }
}