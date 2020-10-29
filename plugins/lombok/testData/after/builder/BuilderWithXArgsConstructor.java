import lombok.Generated;

public class BuilderWithXArgsConstructor {
    private int someProperty;

    @Generated
    public static BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder builder() {
        return new BuilderWithXArgsConstructorBuilder();
    }

    @Generated
    public BuilderWithXArgsConstructor(int someProperty) {
        this.someProperty = someProperty;
    }

    @Generated
    public static class BuilderWithXArgsConstructorBuilder {
        @Generated
        private int someProperty;

        @Generated
        BuilderWithXArgsConstructorBuilder() {
        }

        @Generated
        public BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder someProperty(int someProperty) {
            this.someProperty = someProperty;
            return this;
        }

        @Generated
        public BuilderWithXArgsConstructor build() {
            return new BuilderWithXArgsConstructor(someProperty);
        }

        @Generated
        public String toString() {
            return "BuilderWithXArgsConstructor.BuilderWithXArgsConstructorBuilder(someProperty=" + this.someProperty + ")";
        }
    }
}
