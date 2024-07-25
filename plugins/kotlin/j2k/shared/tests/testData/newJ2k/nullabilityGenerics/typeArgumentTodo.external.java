import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class J {
    public static <T> ArrayList<@Nullable T> nullableList() {
        return new ArrayList<>();
    }

    public static <T> ArrayList<@NotNull T> notNullList() {
        return new ArrayList<>();
    }

    public static <T> ArrayList<T> unknownList() {
        return new ArrayList<>();
    }

    public static <T> ArrayList<String> unrelatedList() {
        return new ArrayList<>();
    }
}