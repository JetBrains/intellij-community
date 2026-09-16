// KTIJ-33418: an Optional instance is never null, only its content is
import java.util.Optional;

public class J {
    private Optional<String> field;

    public Optional<String> returnOptional() {
        return field;
    }

    public void paramOptional(Optional<String> os) {
    }

    public Optional<String> nullableOptional() {
        return null;
    }
}
