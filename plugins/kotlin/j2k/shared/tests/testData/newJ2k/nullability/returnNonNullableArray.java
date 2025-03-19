import org.jetbrains.annotations.NotNull;

class Test {
    public String @NotNull [] someFoo() {
        return new String[]{"a"};
    }
}