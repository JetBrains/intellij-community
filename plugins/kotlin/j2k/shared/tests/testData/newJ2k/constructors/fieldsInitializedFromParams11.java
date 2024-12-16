import org.jetbrains.annotations.NotNull;

public class Bug {
    private final String s;

    public Bug(@NotNull String p, boolean b) {
        if (b) {
            s = p.strip();
        }
        else s = p;
    }
}