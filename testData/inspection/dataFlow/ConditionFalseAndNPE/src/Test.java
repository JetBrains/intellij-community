import org.jetbrains.annotations.Nullable;
public class Foo {
    @Nullable Foo foo() {
        return null;
    }

    public void bar() {
        if (foo() != null &&
          foo().foo() != null &&
          foo().foo().foo() != null) {

        }
    }
}
