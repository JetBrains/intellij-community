import lombok.Builder;
import lombok.NonNull;

public class BuilderOnConstructorWithNonNull {
    private String name;
    private Integer age;
    private Boolean active;
    private String email;

    @Builder
    public BuilderOnConstructorWithNonNull(String name, @NonNull Integer age, Boolean active, @NonNull String email) {
        this.name = name;
        this.age = age;
        this.active = active;
        this.email = email;
    }

    public static void main(String[] args) {
        BuilderOnConstructorWithNonNull.builder().<caret>build();
    }
}