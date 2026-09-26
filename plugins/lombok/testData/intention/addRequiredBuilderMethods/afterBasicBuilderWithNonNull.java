import lombok.Builder;
import lombok.NonNull;

@Builder
public class BasicBuilderWithNonNull {
    private String name;
    @NonNull
    private Integer age;
    private Boolean active;
    @NonNull
    private String email;

    public static void main(String[] args) {
        BasicBuilderWithNonNull.builder().age().email().build();
    }
}