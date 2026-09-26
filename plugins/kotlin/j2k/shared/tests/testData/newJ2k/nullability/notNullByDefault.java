import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
interface Test {
    String str();

    @Nullable
    String nullableStr();
}
