@com.fasterxml.jackson.databind.annotation.JsonDeserialize(builder = BuilderJacksonized.BuilderJacksonizedBuilder.class)
@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someFloat")
public class BuilderJacksonized {

    private int someInt;

    @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
    private String someField;

    BuilderJacksonized(int someInt, String someField) {
        this.someInt = someInt;
        this.someField = someField;
    }

    public static BuilderJacksonizedBuilder builder() {
        return new BuilderJacksonizedBuilder();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("someFloat")
    @com.fasterxml.jackson.annotation.JsonRootName("RootName")
    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "", buildMethodName = "build")
    public static class BuilderJacksonizedBuilder {
        private int someInt;
        private String someField;

        BuilderJacksonizedBuilder() {
        }

        public BuilderJacksonizedBuilder someInt(int someInt) {
            this.someInt = someInt;
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
        public BuilderJacksonizedBuilder someField(String someField) {
            this.someField = someField;
            return this;
        }

        public BuilderJacksonized build() {
            return new BuilderJacksonized(this.someInt, this.someField);
        }

        public String toString() {
            return "BuilderJacksonized.BuilderJacksonizedBuilder(someInt=" + this.someInt + ", someField=" + this.someField + ")";
        }
    }
}