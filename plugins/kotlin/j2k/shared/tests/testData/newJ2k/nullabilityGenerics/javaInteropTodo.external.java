import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class J {
    @Nullable ArrayList<@Nullable String> field1;
    @Nullable ArrayList<@NotNull String> field2;
    @NotNull ArrayList<@Nullable String> field3 = new ArrayList<>();
    @NotNull ArrayList<@NotNull String> field4 = new ArrayList<>();

    public @Nullable ArrayList<@Nullable String> return1() {
        return null;
    }

    public @Nullable ArrayList<@NotNull String> return2() {
        return null;
    }

    public @NotNull ArrayList<@Nullable String> return3() {
        return new ArrayList<>();
    }

    public @NotNull ArrayList<@NotNull String> return4() {
        return new ArrayList<>();
    }

    public void argument1(@Nullable ArrayList<@Nullable String> x) {
    }

    public void argument2(@Nullable ArrayList<@NotNull String> x) {
    }

    public void argument3(@NotNull ArrayList<@Nullable String> x) {
    }

    public void argument4(@NotNull ArrayList<@NotNull String> x) {
    }
}