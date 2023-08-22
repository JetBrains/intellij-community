public final class Foo {
    private Foo() {
    }

    private final boolean IS_LINUX;

    {
        IS_LINUX = true;
    }

    public boolean isLinux() {
        return IS_LINUX;
    }
}