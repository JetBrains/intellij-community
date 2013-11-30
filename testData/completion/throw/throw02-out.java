public class Foo extends jave.lang.Throwable {
    void m() {
        throw ((Foo) null);<caret>
    }
}