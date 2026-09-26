// IGNORE_K2
public class J {
    private final boolean b = true;

    public J() {
        foo(Boolean.TRUE);
        if (b) {
            System.out.println("true");
        }
    }

    private <T> void foo(T value) {
    }
}