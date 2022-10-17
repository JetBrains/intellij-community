public class Test {
    private static boolean foo(String text) {
        return text == null ||
               (text.length() != 2 && text.length() != 3 && asFoo(text) != Foo.BAD) ||
               asFoo(text) == Foo.GOOD;
    }

    private static Foo asFoo(String text) {
        return text.isEmpty() ? Foo.BAD : Foo.GOOD;
    }

    enum Foo {GOOD, BAD}
}