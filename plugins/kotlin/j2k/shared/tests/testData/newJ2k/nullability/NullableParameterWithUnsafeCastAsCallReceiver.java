import org.jetbrains.annotations.Nullable;

class Foo {
    public static void foo(@Nullable Object obj) {
        final int result = ((Integer) obj).compareTo(123);
    }
}