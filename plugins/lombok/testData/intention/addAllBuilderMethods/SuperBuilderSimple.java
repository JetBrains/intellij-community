import lombok.experimental.SuperBuilder;

@SuperBuilder
public class SuperBuilderSimple extends ParentClass {
    private String name;
    private Integer age;
    private Boolean active;

    public static void main(String[] args) {
        SuperBuilderSimple.builder().<caret>build();
    }
}

@SuperBuilder
class ParentClass {
    private String parentField;
}