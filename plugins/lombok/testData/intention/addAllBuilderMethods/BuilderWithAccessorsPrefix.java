import lombok.Builder;
import lombok.experimental.Accessors;

@Builder
@Accessors(prefix = "m")
public class BuilderWithAccessorsPrefix {
    private String mName;
    private Integer mAge;
    private Boolean mActive;

    public static void main(String[] args) {
        BuilderWithAccessorsPrefix.builder().<caret>build();
    }
}