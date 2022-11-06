@com.fasterxml.jackson.databind.annotation.JsonDeserialize(builder = SuperBuilderJacksonized.SuperBuilderJacksonizedBuilderImpl.class)
@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someInt")
public class SuperBuilderJacksonized {

    private int someInt;

    @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
    private String someField;

    protected SuperBuilderJacksonized(SuperBuilderJacksonized.SuperBuilderJacksonizedBuilder<?, ?> b) {
        this.someInt = b.someInt;
        this.someField = b.someField;
    }

    public static SuperBuilderJacksonizedBuilder<?, ?> builder() {
        return new SuperBuilderJacksonizedBuilderImpl();
    }

    public static abstract class SuperBuilderJacksonizedBuilder<C extends SuperBuilderJacksonized, B extends SuperBuilderJacksonizedBuilder<C, B>> {
        private int someInt;
        private String someField;

        public B someInt(int someInt) {
            this.someInt = someInt;
            return self();
        }

        @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
        public B someField(String someField) {
            this.someField = someField;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "SuperBuilderJacksonized.SuperBuilderJacksonizedBuilder(someInt=" + this.someInt + ", someField=" + this.someField + ")";
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("someInt")
    @com.fasterxml.jackson.annotation.JsonRootName("RootName")
    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "", buildMethodName = "build")
    static final class SuperBuilderJacksonizedBuilderImpl extends SuperBuilderJacksonizedBuilder<SuperBuilderJacksonized, SuperBuilderJacksonizedBuilderImpl> {
        private SuperBuilderJacksonizedBuilderImpl() {
        }

        protected SuperBuilderJacksonizedBuilderImpl self() {
            return this;
        }

        public SuperBuilderJacksonized build() {
            return new SuperBuilderJacksonized(this);
        }
    }
}