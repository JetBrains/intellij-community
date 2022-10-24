import static java.lang.Enum.valueOf;

public class Foo {
    public static void main(String[] args) {
        Enum.valueOf(Color.class, "RED");
        foo(Enum.valueOf(Color.class, "RED"));
        valueOf(Color.class, "RED");
        foo(valueOf(Color.class, "RED"));
    }

    private static void foo(Color c) {
    }

    enum Color { RED }
}