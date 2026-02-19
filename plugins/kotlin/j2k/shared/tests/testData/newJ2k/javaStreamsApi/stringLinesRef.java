// IGNORE_K2
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class A {
    void foo(Callable<Stream<String>> ref) {}

    void bar() {
        String s = "test";
        foo(s::lines);
        foo("test"::lines);
    }
}
