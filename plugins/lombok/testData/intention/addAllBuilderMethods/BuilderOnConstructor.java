import lombok.Builder;

public class BuilderOnConstructor {
    private String name;
    private Integer age;
    private Boolean active;

    @Builder
    public BuilderOnConstructor(String name, Integer age, Boolean active) {
        this.name = name;
        this.age = age;
        this.active = active;
    }

    public static void main(String[] args) {
        BuilderOnConstructor.builder().<caret>build();
    }
}