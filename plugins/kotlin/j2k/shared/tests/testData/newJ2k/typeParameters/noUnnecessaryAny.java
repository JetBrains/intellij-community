import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class Test {
    List<String> xs;
    @Nullable List<String> strings;

    public void typeParameterWeirdness() {
        xs = strings != null ? new ArrayList<>(strings) : new ArrayList<String>();
    }
}