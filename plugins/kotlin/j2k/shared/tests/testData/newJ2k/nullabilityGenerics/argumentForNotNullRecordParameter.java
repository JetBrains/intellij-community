// JVM_TARGET: 17
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class C {
    private final ArrayList<String> field = new ArrayList<>();

    public void foo() {
        new D(field);
    }
}

record D(@NotNull ArrayList<@NotNull String> param) {
}