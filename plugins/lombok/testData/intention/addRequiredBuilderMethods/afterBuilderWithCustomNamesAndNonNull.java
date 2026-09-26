import lombok.Builder;
import lombok.NonNull;

@Builder(builderMethodName = "create", buildMethodName = "construct")
public class BuilderWithCustomNamesAndNonNull {
    private String name;

    @NonNull
    private Integer age;

    private Boolean active;

    @NonNull
    private String email;

    public static void main(String[] args) {
        BuilderWithCustomNamesAndNonNull.create().age().email().construct();
    }
}