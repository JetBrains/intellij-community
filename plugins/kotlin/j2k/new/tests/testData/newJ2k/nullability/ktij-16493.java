import org.jetbrains.annotations.NotNull;

class SafeOpener {
    private static void showHeadersPopup() {
        A headersPopup =
                new A<P>() {
                    @Override
                    public String getTextFor(P value) {
                        return "hi";
                    }
                };
    }
}

abstract class B<T> {
    @NotNull
    public abstract String getTextFor(T value);
}

class A<T> extends B<T> {
    @Override
    @NotNull
    public String getTextFor(T value) {
        return value.toString();
    }
}

class P { }