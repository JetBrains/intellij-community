// IGNORE_K2
import org.jetbrains.annotations.Nullable;

class C {
    @Nullable private final String string = getString();

    static String getString() { return x(); }
}