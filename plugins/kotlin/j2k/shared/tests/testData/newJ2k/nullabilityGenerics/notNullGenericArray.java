import org.jetbrains.annotations.NotNull;

import java.util.List;

class J {
    public static <T> @NotNull T @NotNull [] foo(@NotNull T @NotNull [] a) {
        return a;
    }

    static <T> @NotNull T @NotNull [] @NotNull [] twoDimGenericArray(@NotNull T @NotNull [] @NotNull [] arr) {
        return arr;
    }

    static List<@NotNull String>[] arrayOfLists(List<@NotNull String>[] arr) {
        return arr;
    }

    static List<@NotNull String @NotNull []> listOfNotNullArrays(List<@NotNull String @NotNull []> list) {
        return list;
    }
}
