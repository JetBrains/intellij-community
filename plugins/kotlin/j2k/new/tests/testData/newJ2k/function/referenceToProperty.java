import java.util.function.Function;

public class Test {
    private void test() {
        bar(Foo::getContent);
    }

    private void bar(Function<Foo, String> mapper) {
    }
}