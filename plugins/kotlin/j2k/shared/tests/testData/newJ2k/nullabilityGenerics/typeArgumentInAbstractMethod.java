import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

interface J {
    ArrayList<@NotNull String> notNullElements();

    ArrayList<@Nullable String> nullableElements();
}
