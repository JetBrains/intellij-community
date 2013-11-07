import java.lang.*;

public class Foo {
    void m() {
        bar().i<caret>
    }

    java.lang.Boolean bar() {
        return true;
    }
}