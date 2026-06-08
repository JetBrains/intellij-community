import org.jetbrains.annotations.NotNull;

public interface JavaInterface {
    default void foo(@NotNull String s) {
    }
}

