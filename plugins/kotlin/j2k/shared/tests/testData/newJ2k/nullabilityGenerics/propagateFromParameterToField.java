import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class C {
    private final HashMap<String, String> field = new HashMap<>();

    public void foo() {
        baz(field);
    }

    public void baz(HashMap<@NotNull String, @NotNull String> param) {
    }
}