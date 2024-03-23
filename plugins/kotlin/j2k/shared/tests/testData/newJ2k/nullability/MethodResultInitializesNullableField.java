import org.jetbrains.annotations.Nullable;

class C {
    @Nullable
    final String string = getString();

    static String getString() {
        return null;
    }
}