import org.jetbrains.annotations.Nullable;

class Y extends A {
    @Override
    @Nullable
    Object foo(@Nullable String n, int s, @Nullable Long o) {
        return "";
    }
}
