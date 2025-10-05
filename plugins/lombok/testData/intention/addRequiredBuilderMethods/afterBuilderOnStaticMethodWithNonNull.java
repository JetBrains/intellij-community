import lombok.Builder;
import lombok.NonNull;

public class BuilderOnStaticMethodWithNonNull {
    private String name;
    private Integer age;
    private Boolean active;
    private String email;

    private BuilderOnStaticMethodWithNonNull(String name, Integer age, Boolean active, String email) {
        this.name = name;
        this.age = age;
        this.active = active;
        this.email = email;
    }

    @Builder
    public static BuilderOnStaticMethodWithNonNull createInstance(String name,
                                                                 @NonNull Integer age,
                                                                 Boolean active,
                                                                 @NonNull String email) {
        return new BuilderOnStaticMethodWithNonNull(name, age, active, email);
    }

    public static void main(String[] args) {
        BuilderOnStaticMethodWithNonNull.builder().age().email().build();
    }
}