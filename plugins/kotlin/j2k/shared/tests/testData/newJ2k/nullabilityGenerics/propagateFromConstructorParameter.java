import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class C {
    private final HashMap<String, String> field = new HashMap<>();

    public void foo() {
        new D(field);
    }
}

class D {
    public D(HashMap<@NotNull String, @NotNull String> param) {
    }
}