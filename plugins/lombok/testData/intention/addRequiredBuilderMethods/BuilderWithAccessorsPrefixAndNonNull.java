import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Builder
@Accessors(prefix = "m")
public class BuilderWithAccessorsPrefixAndNonNull {
    private String mName;

    @NonNull
    private Integer mAge;

    private Boolean mActive;

    @NonNull
    private String mEmail;

    public static void main(String[] args) {
        BuilderWithAccessorsPrefixAndNonNull.builder().<caret>build();
    }
}