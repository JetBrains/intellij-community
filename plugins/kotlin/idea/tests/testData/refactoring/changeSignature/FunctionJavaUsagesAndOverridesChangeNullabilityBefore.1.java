import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X extends A {
    @NotNull
    @Override
    String foo(int n, @NotNull String s, @Nullable Object o) {
        return "";
    }
}

class Y extends A {
    @Override
    String foo(int n, String s, Object o) {
        return "";
    }
}

class Z extends A {
    @Nullable
    @Override
    String foo(int n, @Nullable String s, @NotNull Object o) {
        return "";
    }
}