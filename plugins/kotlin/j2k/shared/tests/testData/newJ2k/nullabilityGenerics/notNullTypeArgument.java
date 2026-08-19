import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class J {
    List<@NotNull String> simple(List<@NotNull String> list) {
        return list;
    }

    Map<String, List<@NotNull String>> nested(Map<String, List<@NotNull String>> map) {
        return map;
    }

    Collection<? extends @NotNull String> wildcard(Collection<? extends @NotNull String> collection) {
        return collection;
    }

    List<List<@NotNull String>> nestedNotNull(List<List<@NotNull String>> list) {
        return list;
    }

    List<List<@Nullable String>> nestedNullable(List<List<@Nullable String>> list) {
        return list;
    }

    List<@Nullable List<@NotNull String>> outerNullableInnerNotNull(List<@Nullable List<@NotNull String>> list) {
        return list;
    }

    List<@NotNull List<@Nullable String>> outerNotNullInnerNullable(List<@NotNull List<@Nullable String>> list) {
        return list;
    }

    Map<@NotNull String, @Nullable List<@NotNull String>> mapMixed(Map<@NotNull String, @Nullable List<@NotNull String>> map) {
        return map;
    }

    <T> List<@NotNull T> notNullT(List<@NotNull T> list) {
        return list;
    }

    <T> List<@Nullable T> nullableT(List<@Nullable T> list) {
        return list;
    }

    <T> List<List<@NotNull T>> nestedNotNullT(List<List<@NotNull T>> list) {
        return list;
    }

    <T> List<@Nullable List<@NotNull T>> outerNullableInnerNotNullT(List<@Nullable List<@NotNull T>> list) {
        return list;
    }
}
