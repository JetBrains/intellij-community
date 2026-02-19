import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X extends A {
    @Override
    @Nullable
    String foo(int n, @Nullable String s, @NotNull Object o) {
        return "";
    }
}

class Y extends A {
    @Override
    @Nullable
    String foo(int n, @Nullable String s, @NotNull Object o) {
        return "";
    }
}

class Z extends A {
    @Override
    @Nullable
    String foo(int n, @Nullable String s, @NotNull Object o) {
        return "";
    }
}