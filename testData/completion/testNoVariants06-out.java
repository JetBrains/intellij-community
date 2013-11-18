// Items: Boo.Boo2, Boo.Boo3, Boo.new, Boo.var, Boo2.new, Boo2.var, Boo3.new, Boo3.var
public class Boo {
    void m() {
        Boo foo = new Boo();<caret>
    }

    class Boo2 { }
    class Boo3 { }
}