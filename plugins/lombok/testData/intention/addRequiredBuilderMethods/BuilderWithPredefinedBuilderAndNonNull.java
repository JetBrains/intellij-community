import lombok.Builder;
import lombok.NonNull;

@Builder
public class BuilderWithPredefinedBuilderAndNonNull {
    private String name;

    @NonNull
    private Integer age;

    private Boolean active;

    @NonNull
    private String email;

    public static void main(String[] args) {
        BuilderWithPredefinedBuilderAndNonNull.builder().<caret>build();
    }

    public static class BuilderWithPredefinedBuilderAndNonNullBuilder {
        // Predefined builder class with custom implementation
        private String customField;

        public BuilderWithPredefinedBuilderAndNonNullBuilder customMethod() {
            this.customField = "custom";
            return this;
        }
    }
}