public final class Foo {
    private Foo() {
    }

    private static final boolean IS_LINUX;

    static {
        IS_LINUX = true;
    }

    public static boolean isLinux() {
        return IS_LINUX;
    }
}