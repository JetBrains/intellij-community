// JVM_TARGET: 17
import org.jetbrains.annotations.NotNull;

public class J {
    public void test(String s) {
        new D(s);
    }
}

record D(@NotNull String s) {
}