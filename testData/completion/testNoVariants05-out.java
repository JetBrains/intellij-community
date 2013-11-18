// Items: flahBlah.m, flahBlah.arg, flahBlah.notnull, flahBlah.null, fooBar.var
public class fooBar {
    void m(fooBar flahBlah) {
        fooBar foo = new fooBar();<caret>
    }
}