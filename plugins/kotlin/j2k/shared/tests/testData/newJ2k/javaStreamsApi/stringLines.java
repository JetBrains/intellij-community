// IGNORE_K2
// TODO investigate why extension methods are unresolved in this test (imports are not added)
import java.util.stream.Stream;

public class A {
    Stream<String> foo(String s) {
        // comment

        bar().lines();
        Stream<String> lines = s.lines();
        return s.lines();


        // comment
    }

    String bar() {
        return "test";
    }
}
