import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;

public class J {
    private void notNull1(ArrayList<@NotNull String> strings) {
    }

    private void nullable2(ArrayList<@Nullable String> strings) {
    }

    private ArrayList<@NotNull String> notNull3() {
        return new ArrayList<>();
    }

    private ArrayList<@Nullable String> nullable4() {
        return new ArrayList<>();
    }
}
