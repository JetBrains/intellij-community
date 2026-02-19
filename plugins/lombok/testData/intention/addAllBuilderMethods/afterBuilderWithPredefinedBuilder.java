import lombok.Builder;

@Builder
public class BuilderWithPredefinedBuilder {
    private String name;
    private Integer age;
    private Boolean active;

    public static void main(String[] args) {
        BuilderWithPredefinedBuilder.builder().name().age().active().build();
    }

    public static class BuilderWithPredefinedBuilderBuilder {
        // Predefined builder class with custom implementation
        private String customField;

        public BuilderWithPredefinedBuilderBuilder customMethod() {
            this.customField = "custom";
            return this;
        }
    }
}