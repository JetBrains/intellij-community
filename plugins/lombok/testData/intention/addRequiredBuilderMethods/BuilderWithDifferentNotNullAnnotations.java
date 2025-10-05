import lombok.Builder;
import lombok.NonNull;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import javax.validation.constraints.NotNull;

@Builder
public class BuilderWithDifferentNotNullAnnotations {
    private String optionalField;

    @NonNull
    private String lombokNonNull;

    @Nonnull
    private String javaxNonnull;

    @NotNull
    private String jetbrainsNotNull;

    @javax.validation.constraints.NotNull
    private String javaxValidationNotNull;

    public static void main(String[] args) {
        BuilderWithDifferentNotNullAnnotations.builder().<caret>build();
    }
}