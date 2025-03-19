import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// TODO handle the case when type argument is used in the method return type (make it not-null)
public class J {
    Set<String> notNullSet = Collections.emptySet();

    Collection<String> notNullCollection = Collections.emptyList();
    Collection<String> nullableCollection = Collections.emptyList();

    List<@NotNull String> notNullList = List.of();
    List<@Nullable String> nullableList = List.of();

    void foo() {
        for (String s : notNullSet) {
            System.out.println(s.length());
        }
        for (String s : notNullCollection) {
            System.out.println(s.length());
        }
        for (String s : nullableCollection) {
            if (s != null) {
                System.out.println(s.length());
            }
        }

        takeNotNullCollection(Collections.emptyList());
        takeNotNullCollection(returnNotNullCollection());
    }

    private void takeNotNullCollection(Collection<@NotNull String> strings) {
    }

    Collection<String> returnNotNullCollection() {
        return Collections.emptyList();
    }
}