import lombok.Builder;

@Builder(toBuilder = true)
public class BuilderWithDefaultInnerFields {
    @Builder.Default
    private Object bar = new Object();

    public static class BuilderWithDefaultInnerFieldsBuilder {
        public BuilderWithDefaultInnerFieldsBuilder barManual(Object bar) {
            this.bar$value = bar;
            this.bar$set = true;
            return this;
        }
    }
}
