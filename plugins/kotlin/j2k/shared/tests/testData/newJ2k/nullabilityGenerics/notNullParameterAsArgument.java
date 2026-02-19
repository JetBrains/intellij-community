import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;

class TypeArgumentOnly {
    private void notNull(ArrayList<@NotNull String> strings) {
        foo(strings);
    }

    private void foo(ArrayList<String> strings) {
    }
}

class DeeplyNotNull {
    private void notNull(@NotNull ArrayList<@NotNull String> strings) {
        foo(strings);
    }

    // top-level ArrayList can still be nullable
    private void foo(ArrayList<String> strings) {
    }
}
