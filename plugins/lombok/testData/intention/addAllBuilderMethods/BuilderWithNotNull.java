import lombok.Builder;
import lombok.NonNull;

@Builder
public class BuilderWithNotNull {
    private String name;
    @NonNull
    private Integer age;
    private Boolean active;

    public static void main(String[] args) {
        BuilderWithNotNull.builder().<caret>build();
    }
}