import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class J {
    public static <T> T unannotated(T value) {
        return value;
    }

    public static <@NotNull T> T notNullTypeParameter(T value) {
        return value;
    }

    public static <@Nullable T> T nullableTypeParameter(T value) {
        return value;
    }

    public static <T extends String> T notNullBound(T value) {
        return value;
    }

    public static <K, V> void twoTypeParameters(K key, V value) {
    }
}
