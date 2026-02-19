import lombok.Builder;

@Builder(builderMethodName = "create", buildMethodName = "construct")
public class BuilderWithCustomNames {
    private String name;
    private Integer age;
    private Boolean active;

    public static void main(String[] args) {
        BuilderWithCustomNames.create().name().age().active().construct();
    }
}