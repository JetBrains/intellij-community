import lombok.Builder;

public class BuilderOnStaticMethod {
    private String name;
    private Integer age;
    private Boolean active;

    private BuilderOnStaticMethod(String name, Integer age, Boolean active) {
        this.name = name;
        this.age = age;
        this.active = active;
    }

    @Builder
    public static BuilderOnStaticMethod createInstance(String name, Integer age, Boolean active) {
        return new BuilderOnStaticMethod(name, age, active);
    }

    public static void main(String[] args) {
        BuilderOnStaticMethod.builder().name().age().active().build();
    }
}