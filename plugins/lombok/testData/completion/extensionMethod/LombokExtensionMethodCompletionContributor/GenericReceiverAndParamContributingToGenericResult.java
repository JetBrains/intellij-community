import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
    void test(String string) {
        string.<caret>
    }

    static class Pair<T, S> {}

    static class Extensions {
        public static <T, S> Pair<T, S> pairWith(T e1, S e2) {
            return new Pair<T, S>();
        }
    }
}