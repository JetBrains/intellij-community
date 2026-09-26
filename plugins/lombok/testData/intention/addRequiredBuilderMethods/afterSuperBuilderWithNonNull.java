import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SuperBuilderWithNonNull extends ParentClass {
    private String name;
    @NonNull
    private Integer age;
    private Boolean active;
    @NonNull
    private String email;

    public static void main(String[] args) {
        SuperBuilderWithNonNull.builder().age().email().requiredParentField().build();
    }
}

@SuperBuilder
class ParentClass {
    private String parentField;
    @NonNull
    private String requiredParentField;
}